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

import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses generic signatures from class files into human-readable Java type strings.
 */
public final class SignatureParser {

    private SignatureParser() {
    }

    /**
     * Parse a field type signature into a readable Java type string with generics.
     */
    public static String parseFieldType(final String signature) {
        final SignatureReader reader = new SignatureReader(signature);
        final TypeBuilder builder = new TypeBuilder();
        reader.acceptType(builder);
        return builder.toString();
    }

    /**
     * Parse a method signature and return the parameter types and return type.
     */
    public static MethodSignature parseMethodSignature(final String signature) {
        final SignatureReader reader = new SignatureReader(signature);
        final MethodSignatureVisitor visitor = new MethodSignatureVisitor();
        reader.accept(visitor);
        return new MethodSignature(visitor.getParameterTypes(), visitor.getReturnType());
    }

    public static final class MethodSignature {

        private final List<String> parameterTypes;
        private final String returnType;

        public MethodSignature(final List<String> parameterTypes, final String returnType) {
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
        }

        public List<String> getParameterTypes() {
            return parameterTypes;
        }

        public String getReturnType() {
            return returnType;
        }
    }

    private static final class MethodSignatureVisitor extends SignatureVisitor {

        private final List<String> parameterTypes = new ArrayList<>();
        private String returnType = "void";
        private TypeBuilder currentParam;
        private TypeBuilder currentReturn;

        MethodSignatureVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public SignatureVisitor visitParameterType() {
            if (currentParam != null) {
                parameterTypes.add(currentParam.toString());
            }
            currentParam = new TypeBuilder();
            return currentParam;
        }

        @Override
        public SignatureVisitor visitReturnType() {
            if (currentParam != null) {
                parameterTypes.add(currentParam.toString());
                currentParam = null;
            }
            currentReturn = new TypeBuilder();
            return currentReturn;
        }

        @Override
        public void visitEnd() {
            if (currentReturn != null) {
                returnType = currentReturn.toString();
            }
        }

        List<String> getParameterTypes() {
            return parameterTypes;
        }

        String getReturnType() {
            return returnType;
        }
    }

    /**
     * Builds a human-readable type string from signature visitor callbacks.
     */
    static class TypeBuilder extends SignatureVisitor {

        private final StringBuilder sb = new StringBuilder();
        private boolean hasTypeArgs = false;
        private boolean firstTypeArg = true;
        private int arrayDimensions = 0;

        TypeBuilder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitBaseType(final char descriptor) {
            sb.append(Type.getType(String.valueOf(descriptor)).getClassName());
            appendArrayDimensions();
        }

        @Override
        public void visitClassType(final String name) {
            sb.append(name.replace('/', '.'));
        }

        @Override
        public void visitTypeArgument() {
            startTypeArg();
            sb.append("?");
        }

        @Override
        public SignatureVisitor visitTypeArgument(final char wildcard) {
            startTypeArg();
            if (wildcard == '+') {
                sb.append("? extends ");
            } else if (wildcard == '-') {
                sb.append("? super ");
            }
            return new TypeBuilder() {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    TypeBuilder.this.sb.append(this.toString());
                }
            };
        }

        private void startTypeArg() {
            if (!hasTypeArgs) {
                sb.append("<");
                hasTypeArgs = true;
                firstTypeArg = true;
            }
            if (!firstTypeArg) {
                sb.append(", ");
            }
            firstTypeArg = false;
        }

        @Override
        public void visitEnd() {
            if (hasTypeArgs) {
                sb.append(">");
                hasTypeArgs = false;
            }
            appendArrayDimensions();
        }

        @Override
        public SignatureVisitor visitArrayType() {
            arrayDimensions++;
            return this;
        }

        @Override
        public void visitTypeVariable(final String name) {
            sb.append(name);
            appendArrayDimensions();
        }

        private void appendArrayDimensions() {
            for (int i = 0; i < arrayDimensions; i++) {
                sb.append("[]");
            }
            arrayDimensions = 0;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
