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
package org.tomitribe.jackknife.index;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.tomitribe.jackknife.scanner.ClassStructure;
import org.tomitribe.jackknife.scanner.JarScanner;
import org.tomitribe.jackknife.scanner.SignatureParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Writes a JarScanner.Result to a flat, greppable index file.
 */
public final class IndexWriter {

    private IndexWriter() {
    }

    public static void write(final JarScanner.Result result, final File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();

        try (final PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
            for (final ClassStructure cls : result.getClasses()) {
                writeClass(out, cls);
                out.println();
            }

            final List<String> resources = result.getResources();
            if (!resources.isEmpty()) {
                out.println("# resources");
                for (final String resource : resources) {
                    out.println(resource);
                }
            }
        }
    }

    private static void writeClass(final PrintWriter out, final ClassStructure cls) {
        // Class header
        out.println("# " + cls.getName());

        // Class declaration
        final StringBuilder classLine = new StringBuilder();
        appendModifiers(classLine, cls.getAccess(), true);

        if ((cls.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            classLine.append("interface ");
        } else if ((cls.getAccess() & Opcodes.ACC_ENUM) != 0) {
            classLine.append("enum ");
        } else if ((cls.getAccess() & Opcodes.ACC_ANNOTATION) != 0) {
            classLine.append("@interface ");
        } else if ((cls.getAccess() & Opcodes.ACC_RECORD) != 0) {
            classLine.append("record ");
        } else {
            classLine.append("class ");
        }

        classLine.append(cls.getName());

        if (cls.getSuperclass() != null && !"java.lang.Object".equals(cls.getSuperclass())
                && !"java.lang.Enum".equals(cls.getSuperclass())
                && !"java.lang.Record".equals(cls.getSuperclass())) {
            classLine.append(" extends ").append(cls.getSuperclass());
        }

        out.println(classLine);

        // Interfaces
        for (final String iface : cls.getInterfaces()) {
            out.println("implements " + iface);
        }

        // Class annotations
        for (final String annotation : cls.getAnnotations()) {
            out.println("@" + annotation);
        }

        // Fields
        for (final ClassStructure.FieldInfo field : cls.getFields()) {
            // Skip synthetic fields
            if ((field.getAccess() & Opcodes.ACC_SYNTHETIC) != 0) {
                continue;
            }

            final StringBuilder fieldLine = new StringBuilder("  ");
            for (final String annotation : field.getAnnotations()) {
                fieldLine.append("@").append(annotation).append(" ");
            }
            appendModifiers(fieldLine, field.getAccess(), false);
            fieldLine.append("field ").append(field.getName()).append(" : ").append(field.getType());
            out.println(fieldLine);
        }

        // Methods
        for (final ClassStructure.MethodInfo method : cls.getMethods()) {
            // Skip synthetic methods, static initializers
            if ((method.getAccess() & Opcodes.ACC_SYNTHETIC) != 0) {
                continue;
            }
            if ("<clinit>".equals(method.getName())) {
                continue;
            }

            final StringBuilder methodLine = new StringBuilder("  ");
            for (final String annotation : method.getAnnotations()) {
                methodLine.append("@").append(annotation).append(" ");
            }
            appendModifiers(methodLine, method.getAccess(), false);
            methodLine.append("method ");

            // Method name
            methodLine.append(method.getName());

            // Parameters
            methodLine.append("(");
            final Type[] argTypes = Type.getArgumentTypes(method.getDescriptor());
            final List<String> paramNames = method.getParameterNames();

            // Try to parse generic parameter types from signature
            List<String> genericParamTypes = null;
            String genericReturnType = null;
            if (method.getSignature() != null && !method.getSignature().equals(method.getDescriptor())) {
                try {
                    final SignatureParser.MethodSignature ms = SignatureParser.parseMethodSignature(method.getSignature());
                    if (ms.getParameterTypes().size() == argTypes.length) {
                        genericParamTypes = ms.getParameterTypes();
                    }
                    genericReturnType = ms.getReturnType();
                } catch (final Exception e) {
                    // Fall back to descriptor-based types
                }
            }

            for (int i = 0; i < argTypes.length; i++) {
                if (i > 0) {
                    methodLine.append(", ");
                }
                final String typeName = genericParamTypes != null ? genericParamTypes.get(i) : argTypes[i].getClassName();
                methodLine.append(typeName);
                if (i < paramNames.size()) {
                    methodLine.append(" ").append(paramNames.get(i));
                }
            }
            methodLine.append(")");

            // Return type (skip for constructors)
            if (!"<init>".equals(method.getName())) {
                final String returnType = genericReturnType != null ? genericReturnType : Type.getReturnType(method.getDescriptor()).getClassName();
                methodLine.append(" : ").append(returnType);
            }

            out.println(methodLine);

            // Declared exceptions
            for (final String ex : method.getDeclaredExceptions()) {
                out.println("    declares " + ex);
            }

            // Thrown exceptions (from method body)
            for (final String ex : method.getThrownExceptions()) {
                out.println("    throws " + ex);
            }
        }
    }

    private static void appendModifiers(final StringBuilder sb, final int access, final boolean isClass) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            sb.append("public ");
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            sb.append("protected ");
        } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
            sb.append("private ");
        }

        if ((access & Opcodes.ACC_STATIC) != 0) {
            sb.append("static ");
        }

        if (!isClass) {
            if ((access & Opcodes.ACC_FINAL) != 0) {
                sb.append("final ");
            }
        } else {
            if ((access & Opcodes.ACC_ABSTRACT) != 0 && (access & Opcodes.ACC_INTERFACE) == 0) {
                sb.append("abstract ");
            }
            if ((access & Opcodes.ACC_FINAL) != 0) {
                sb.append("final ");
            }
        }

        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0 && !isClass) {
            sb.append("synchronized ");
        }

        if ((access & Opcodes.ACC_NATIVE) != 0) {
            sb.append("native ");
        }
    }
}
