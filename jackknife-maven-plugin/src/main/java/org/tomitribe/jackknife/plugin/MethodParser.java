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
package org.tomitribe.jackknife.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Forgiving method parser that accepts various input formats and normalizes
 * to the Snitch-compatible form: com.example.Foo.method(type1,type2)
 *
 * Accepts:
 *   com.example.Foo.process(java.lang.String,int)           -- Snitch format
 *   com.example.Foo.process(java.lang.String name, int x)   -- index format with param names
 *   public method process(String input, int count) : boolean -- full index line (needs class)
 *   process                                                  -- method name only (needs class)
 *   process(int, String)                                     -- method + args (needs class)
 */
public final class MethodParser {

    private MethodParser() {
    }

    /**
     * Parse a method specification into its components.
     * Returns null if the input cannot be parsed.
     */
    public static ParsedMethod parse(final String input, final String defaultClass) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input.trim();

        // Strip leading modifiers: public, private, protected, static, final, synchronized, native, abstract
        cleaned = cleaned.replaceAll("^(public|private|protected|static|final|synchronized|native|abstract|method)\\s+", "");
        // Repeat to handle multiple modifiers like "public static method"
        cleaned = cleaned.replaceAll("^(public|private|protected|static|final|synchronized|native|abstract|method)\\s+", "");
        cleaned = cleaned.replaceAll("^(public|private|protected|static|final|synchronized|native|abstract|method)\\s+", "");
        cleaned = cleaned.replaceAll("^(public|private|protected|static|final|synchronized|native|abstract|method)\\s+", "");

        // Strip return type: " : something" at the end
        cleaned = cleaned.replaceAll("\\s*:\\s*\\S+$", "");

        // Strip annotation prefixes like "@javax.inject.Inject "
        cleaned = cleaned.replaceAll("^@\\S+\\s+", "");

        // Now we should have either:
        //   com.example.Foo.method(args)
        //   com.example.Foo.method()
        //   com.example.Foo.method
        //   method(args)
        //   method()
        //   method

        String className = null;
        String methodName;
        String argsString = null;

        // Check if there are parentheses
        final int parenOpen = cleaned.indexOf('(');
        if (parenOpen >= 0) {
            final int parenClose = cleaned.lastIndexOf(')');
            if (parenClose < 0) {
                return null;
            }

            final String beforeParen = cleaned.substring(0, parenOpen).trim();
            argsString = cleaned.substring(parenOpen + 1, parenClose).trim();

            // Is there a class.method or just method?
            final int lastDot = beforeParen.lastIndexOf('.');
            if (lastDot >= 0 && looksLikeClassName(beforeParen.substring(0, lastDot))) {
                className = beforeParen.substring(0, lastDot);
                methodName = beforeParen.substring(lastDot + 1);
            } else {
                methodName = beforeParen;
            }
        } else {
            // No parens — just a name, possibly with class prefix
            final int lastDot = cleaned.lastIndexOf('.');
            if (lastDot >= 0 && looksLikeClassName(cleaned.substring(0, lastDot))) {
                className = cleaned.substring(0, lastDot);
                methodName = cleaned.substring(lastDot + 1);
            } else {
                methodName = cleaned;
            }
        }

        // Apply default class if not found in the input
        if (className == null || className.isEmpty()) {
            className = defaultClass;
        }

        // Parse and clean args — strip parameter names, keep only types
        final List<String> argTypes = parseArgs(argsString);

        return new ParsedMethod(className, methodName, argTypes);
    }

    /**
     * Parse argument string, stripping parameter names and keeping only types.
     * Returns null if no args specified (wildcard match).
     */
    private static List<String> parseArgs(final String argsString) {
        if (argsString == null) {
            return null; // no parens in input — wildcard match
        }
        if (argsString.isEmpty()) {
            return new ArrayList<>(); // explicit empty parens — zero-arg match
        }

        final List<String> types = new ArrayList<>();
        final String[] parts = argsString.split(",");
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Could be "java.lang.String name" or just "java.lang.String" or "String"
            // The type is the first token, the name (if present) is the second
            final String[] tokens = trimmed.split("\\s+");
            String typeName = tokens[0];

            // Handle simple names — expand common ones
            typeName = expandSimpleType(typeName);

            types.add(typeName);
        }

        return types;
    }

    /**
     * Expand simple type names to fully qualified names for common types.
     */
    private static String expandSimpleType(final String typeName) {
        return switch (typeName) {
            case "String" -> "java.lang.String";
            case "Object" -> "java.lang.Object";
            case "Class" -> "java.lang.Class";
            case "Integer" -> "java.lang.Integer";
            case "Long" -> "java.lang.Long";
            case "Boolean" -> "java.lang.Boolean";
            case "Double" -> "java.lang.Double";
            case "Float" -> "java.lang.Float";
            case "Byte" -> "java.lang.Byte";
            case "Short" -> "java.lang.Short";
            case "Character" -> "java.lang.Character";
            case "List" -> "java.util.List";
            case "Map" -> "java.util.Map";
            case "Set" -> "java.util.Set";
            case "Collection" -> "java.util.Collection";
            default -> typeName;
        };
    }

    /**
     * Check if a string looks like a class name (has at least one dot and the part
     * after the last dot starts with uppercase).
     */
    private static boolean looksLikeClassName(final String candidate) {
        if (!candidate.contains(".")) {
            return false;
        }
        final int lastDot = candidate.lastIndexOf('.');
        if (lastDot >= candidate.length() - 1) {
            return false;
        }
        return Character.isUpperCase(candidate.charAt(lastDot + 1));
    }

    /**
     * A parsed method specification.
     */
    public static final class ParsedMethod {

        private final String className;
        private final String methodName;
        private final List<String> argTypes; // null means wildcard (any args)

        public ParsedMethod(final String className, final String methodName, final List<String> argTypes) {
            this.className = className;
            this.methodName = methodName;
            this.argTypes = argTypes;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public List<String> getArgTypes() {
            return argTypes;
        }

        /**
         * Whether this targets a specific class or is a wildcard (method name only).
         */
        public boolean hasClass() {
            return className != null && !className.isEmpty();
        }

        /**
         * Whether args were specified (even if empty for zero-arg methods).
         */
        public boolean hasArgs() {
            return argTypes != null;
        }

        /**
         * Format as Snitch-compatible string: com.example.Foo.method(type1,type2)
         */
        public String toSnitchFormat() {
            final StringBuilder sb = new StringBuilder();
            if (className != null) {
                sb.append(className).append(".");
            }
            sb.append(methodName).append("(");
            if (argTypes != null) {
                sb.append(String.join(",", argTypes));
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String toString() {
            return toSnitchFormat();
        }
    }
}
