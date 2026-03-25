/*
 * Licensed to Tomitribe Corporation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. Tomitribe Corporation licenses
 * this file to You under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.tomitribe.jackknife.transform;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Set;

/**
 * ASM-based bytecode transformer that renames target methods and creates
 * wrapper methods that delegate to an InvocationHandler.
 *
 * The wrapper method:
 * 1. Looks up the InvocationHandler from a registry
 * 2. Boxes all arguments into Object[]
 * 3. Calls handler.invoke(this, method, args)
 * 4. Unboxes and returns the result
 *
 * The original method is renamed to jackknife$originalName and retains
 * its original bytecode. Annotations are moved to the wrapper.
 */
public final class HandlerEnhancer {

    private static final String PREFIX = "jackknife$";
    private static final String REGISTRY_CLASS = "org/tomitribe/jackknife/runtime/HandlerRegistry";
    private static final String HANDLER_IFACE = "java/lang/reflect/InvocationHandler";

    private HandlerEnhancer() {
    }

    /**
     * Transform the given class bytes, wrapping the specified methods with InvocationHandler delegation.
     *
     * @param classBytes original class bytecode
     * @param methodsToWrap set of method names to wrap (name only, wraps all overloads)
     * @return transformed class bytecode
     */
    public static byte[] enhance(final byte[] classBytes, final Set<String> methodsToWrap) {
        final ClassReader reader = new ClassReader(classBytes);
        final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        reader.accept(new EnhancerVisitor(writer, methodsToWrap), 0);

        return writer.toByteArray();
    }

    private static class EnhancerVisitor extends ClassVisitor {

        private final Set<String> methodsToWrap;
        private String classInternalName;

        EnhancerVisitor(final ClassWriter writer, final Set<String> methodsToWrap) {
            super(Opcodes.ASM9, writer);
            this.methodsToWrap = methodsToWrap;
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature,
                          final String superName, final String[] interfaces) {
            this.classInternalName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                         final String signature, final String[] exceptions) {
            if (!methodsToWrap.contains(name) || "<init>".equals(name) || "<clinit>".equals(name)) {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            // Rename the original method
            final MethodVisitor movedMethod = super.visitMethod(access, PREFIX + name, descriptor, signature, exceptions);

            // Create the wrapper method with the original name
            final MethodVisitor wrapperMethod = createWrapper(access, name, descriptor, signature, exceptions);

            // Return a visitor that moves annotations from the original to the wrapper
            return new MoveAnnotationsVisitor(movedMethod, wrapperMethod);
        }

        /**
         * Generate the wrapper method that delegates to InvocationHandler.
         *
         * Conceptually generates:
         * <pre>
         * public ReturnType method(ArgType1 arg1, ArgType2 arg2) {
         *     InvocationHandler handler = HandlerRegistry.getHandler("com.example.Class", "method", "(Ljava/lang/String;I)V");
         *     if (handler == null) {
         *         return jackknife$method(arg1, arg2);
         *     }
         *     Object[] args = new Object[] { arg1, arg2 };
         *     Object result = handler.invoke(this, null, args);
         *     return (ReturnType) result;
         * }
         * </pre>
         */
        private MethodVisitor createWrapper(final int access, final String name, final String descriptor,
                                            final String signature, final String[] exceptions) {
            // Remove synchronized from wrapper — let the original method handle it
            final int wrapperAccess = access & ~Opcodes.ACC_SYNCHRONIZED;

            final MethodVisitor mv = cv.visitMethod(wrapperAccess, name, descriptor, signature, exceptions);
            mv.visitCode();

            final Type[] argTypes = Type.getArgumentTypes(descriptor);
            final Type returnType = Type.getReturnType(descriptor);
            final boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

            // HandlerRegistry.getHandler(className, methodName)
            mv.visitLdcInsn(classInternalName.replace('/', '.'));
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, REGISTRY_CLASS, "getHandler",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/reflect/InvocationHandler;",
                    false);

            // Store handler in local var
            final int handlerVar = computeLocalOffset(argTypes, isStatic);
            mv.visitVarInsn(Opcodes.ASTORE, handlerVar);

            // if (handler == null) { return jackknife$method(args...); }
            mv.visitVarInsn(Opcodes.ALOAD, handlerVar);
            final Label handlerNotNull = new Label();
            mv.visitJumpInsn(Opcodes.IFNONNULL, handlerNotNull);

            // Direct call to renamed method (fallback when no handler registered)
            loadArgs(mv, argTypes, isStatic);
            mv.visitMethodInsn(
                    isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                    classInternalName, PREFIX + name, descriptor, false);
            emitReturn(mv, returnType);

            mv.visitLabel(handlerNotNull);

            // Create Object[] args array
            mv.visitIntInsn(Opcodes.BIPUSH, argTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            final int argsArrayVar = handlerVar + 1;
            mv.visitVarInsn(Opcodes.ASTORE, argsArrayVar);

            // Fill the array
            int argSlot = isStatic ? 0 : 1;
            for (int i = 0; i < argTypes.length; i++) {
                mv.visitVarInsn(Opcodes.ALOAD, argsArrayVar);
                mv.visitIntInsn(Opcodes.BIPUSH, i);
                loadAndBox(mv, argTypes[i], argSlot);
                mv.visitInsn(Opcodes.AASTORE);
                argSlot += argTypes[i].getSize();
            }

            // handler.invoke(this, null, args)
            mv.visitVarInsn(Opcodes.ALOAD, handlerVar);
            if (isStatic) {
                mv.visitInsn(Opcodes.ACONST_NULL); // no 'this' for static methods
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            }
            mv.visitInsn(Opcodes.ACONST_NULL); // Method parameter — resolved by handler
            mv.visitVarInsn(Opcodes.ALOAD, argsArrayVar);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, HANDLER_IFACE, "invoke",
                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                    true);

            // Unbox and return
            if (returnType.equals(Type.VOID_TYPE)) {
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                unboxAndReturn(mv, returnType);
            }

            mv.visitMaxs(-1, -1);
            mv.visitEnd();
            return mv;
        }

        private static void loadArgs(final MethodVisitor mv, final Type[] argTypes, final boolean isStatic) {
            int slot = isStatic ? 0 : 1;
            if (!isStatic) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            }
            for (final Type argType : argTypes) {
                mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), slot);
                slot += argType.getSize();
            }
        }

        private static int computeLocalOffset(final Type[] argTypes, final boolean isStatic) {
            int offset = isStatic ? 0 : 1;
            for (final Type argType : argTypes) {
                offset += argType.getSize();
            }
            return offset;
        }

        private static void emitReturn(final MethodVisitor mv, final Type returnType) {
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
        }

        private static void loadAndBox(final MethodVisitor mv, final Type type, final int slot) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
                case Type.BYTE:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    break;
                case Type.CHAR:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    break;
                case Type.SHORT:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    break;
                case Type.INT:
                    mv.visitVarInsn(Opcodes.ILOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(Opcodes.LLOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(Opcodes.FLOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(Opcodes.DLOAD, slot);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    break;
                default:
                    mv.visitVarInsn(Opcodes.ALOAD, slot);
                    break;
            }
        }

        private static void unboxAndReturn(final MethodVisitor mv, final Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.BYTE:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "byteValue", "()B", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.CHAR:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.SHORT:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "shortValue", "()S", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.INT:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.LONG:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false);
                    mv.visitInsn(Opcodes.LRETURN);
                    break;
                case Type.FLOAT:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false);
                    mv.visitInsn(Opcodes.FRETURN);
                    break;
                case Type.DOUBLE:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
                    mv.visitInsn(Opcodes.DRETURN);
                    break;
                default:
                    mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
                    mv.visitInsn(Opcodes.ARETURN);
                    break;
            }
        }
    }

    /**
     * Moves annotations from the renamed method to the wrapper method.
     */
    private static class MoveAnnotationsVisitor extends MethodVisitor {

        private final MethodVisitor wrapper;

        MoveAnnotationsVisitor(final MethodVisitor renamed, final MethodVisitor wrapper) {
            super(Opcodes.ASM9, renamed);
            this.wrapper = wrapper;
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            return wrapper.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(final int parameter, final String descriptor,
                                                          final boolean visible) {
            return wrapper.visitParameterAnnotation(parameter, descriptor, visible);
        }
    }
}
