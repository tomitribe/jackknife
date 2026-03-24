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
package org.tomitribe.jackknife.scanner;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ASM ClassVisitor that extracts structural metadata from a class file.
 * Reads everything including code (no SKIP_CODE) to capture thrown exceptions.
 */
public final class StructureVisitor extends ClassVisitor {

    private String className;
    private String superclass;
    private int classAccess;
    private final List<String> interfaces = new ArrayList<>();
    private final List<String> classAnnotations = new ArrayList<>();
    private final List<ClassStructure.FieldInfo> fields = new ArrayList<>();
    private final List<ClassStructure.MethodInfo> methods = new ArrayList<>();

    public StructureVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        this.className = toJavaName(name);
        this.superclass = superName != null ? toJavaName(superName) : null;
        this.classAccess = access;
        if (interfaces != null) {
            for (final String iface : interfaces) {
                this.interfaces.add(toJavaName(iface));
            }
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        classAnnotations.add(descriptorToJavaName(descriptor));
        return null;
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String descriptor,
                                   final String signature, final Object value) {
        final String type = signature != null ? SignatureParser.parseFieldType(signature) : typeToJavaName(Type.getType(descriptor));
        final List<String> fieldAnnotations = new ArrayList<>();
        return new FieldVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                fieldAnnotations.add(descriptorToJavaName(descriptor));
                return null;
            }

            @Override
            public void visitEnd() {
                fields.add(new ClassStructure.FieldInfo(name, type, signature, fieldAnnotations, access));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        final List<String> methodAnnotations = new ArrayList<>();
        final List<String> parameterNames = new ArrayList<>();
        final Set<String> thrownExceptions = new LinkedHashSet<>();
        final List<String> declaredExceptions = new ArrayList<>();

        if (exceptions != null) {
            for (final String ex : exceptions) {
                declaredExceptions.add(toJavaName(ex));
            }
        }

        return new MethodVisitor(Opcodes.ASM9) {

            private String lastNewException;

            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                methodAnnotations.add(descriptorToJavaName(descriptor));
                return null;
            }

            @Override
            public void visitLocalVariable(final String varName, final String varDescriptor,
                                           final String varSignature, final Label start,
                                           final Label end, final int index) {
                // index 0 is 'this' for instance methods
                final boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                final int paramCount = Type.getArgumentTypes(descriptor).length;
                final int firstParamIndex = isStatic ? 0 : 1;

                if (index >= firstParamIndex && index < firstParamIndex + paramCount) {
                    // Ensure list is large enough
                    final int paramIndex = index - firstParamIndex;
                    while (parameterNames.size() <= paramIndex) {
                        parameterNames.add(null);
                    }
                    if (parameterNames.get(paramIndex) == null) {
                        parameterNames.set(paramIndex, varName);
                    }
                }
            }

            @Override
            public void visitTypeInsn(final int opcode, final String type) {
                if (opcode == Opcodes.NEW) {
                    lastNewException = type;
                } else {
                    lastNewException = null;
                }
            }

            @Override
            public void visitMethodInsn(final int opcode, final String owner, final String methodName,
                                        final String methodDescriptor, final boolean isInterface) {
                // Track NEW SomeException followed by <init> — confirms it's an exception construction
                if ("<init>".equals(methodName) && lastNewException != null && lastNewException.equals(owner)) {
                    // Check if it looks like an exception (ends with Exception or Error)
                    final String javaName = toJavaName(owner);
                    if (javaName.endsWith("Exception") || javaName.endsWith("Error")) {
                        thrownExceptions.add(javaName);
                    }
                }
                lastNewException = null;
            }

            @Override
            public void visitInsn(final int opcode) {
                // DUP and ATHROW are expected between NEW and <init> — don't reset
                if (opcode != Opcodes.ATHROW && opcode != Opcodes.DUP
                        && opcode != Opcodes.DUP_X1 && opcode != Opcodes.DUP_X2) {
                    lastNewException = null;
                }
            }

            @Override
            public void visitIntInsn(final int opcode, final int operand) {
                // int operands can be constructor args — don't reset
            }

            @Override
            public void visitVarInsn(final int opcode, final int varIndex) {
                // var loads can be constructor args — don't reset
            }

            @Override
            public void visitFieldInsn(final int opcode, final String owner, final String fieldName,
                                       final String fieldDescriptor) {
                // field loads can be constructor args — don't reset
            }

            @Override
            public void visitJumpInsn(final int opcode, final Label label) {
                lastNewException = null;
            }

            @Override
            public void visitLdcInsn(final Object value) {
                // LDC can appear between NEW and <init> (loading constructor args) — don't reset
            }

            @Override
            public void visitEnd() {
                // Fill in any missing parameter names with argN
                final Type[] argTypes = Type.getArgumentTypes(descriptor);
                while (parameterNames.size() < argTypes.length) {
                    parameterNames.add(null);
                }
                for (int i = 0; i < parameterNames.size(); i++) {
                    if (parameterNames.get(i) == null) {
                        parameterNames.set(i, "arg" + i);
                    }
                }

                // Remove thrown exceptions that are also declared — they'll show under 'declares'
                thrownExceptions.removeAll(declaredExceptions);

                final String methodSignature = signature != null ? signature : descriptor;

                methods.add(new ClassStructure.MethodInfo(
                        name, descriptor, methodSignature, methodAnnotations,
                        parameterNames, declaredExceptions,
                        new ArrayList<>(thrownExceptions), access
                ));
            }
        };
    }

    public ClassStructure build() {
        return new ClassStructure(className, superclass, interfaces, classAnnotations, fields, methods, classAccess);
    }

    static String toJavaName(final String internalName) {
        return internalName.replace('/', '.');
    }

    static String descriptorToJavaName(final String descriptor) {
        final Type type = Type.getType(descriptor);
        return type.getClassName();
    }

    static String typeToJavaName(final Type type) {
        return type.getClassName();
    }
}
