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

import java.util.List;

/**
 * Structural metadata for a single class, extracted via ASM.
 * This is the data model that gets written to index files.
 */
public final class ClassStructure {

    private final String name;
    private final String superclass;
    private final List<String> interfaces;
    private final List<String> annotations;
    private final List<FieldInfo> fields;
    private final List<MethodInfo> methods;
    private final int access;

    public ClassStructure(final String name, final String superclass, final List<String> interfaces,
                          final List<String> annotations, final List<FieldInfo> fields,
                          final List<MethodInfo> methods, final int access) {
        this.name = name;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.annotations = annotations;
        this.fields = fields;
        this.methods = methods;
        this.access = access;
    }

    public String getName() {
        return name;
    }

    public String getSuperclass() {
        return superclass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public List<FieldInfo> getFields() {
        return fields;
    }

    public List<MethodInfo> getMethods() {
        return methods;
    }

    public int getAccess() {
        return access;
    }

    public static final class FieldInfo {

        private final String name;
        private final String type;
        private final String signature;
        private final List<String> annotations;
        private final int access;

        public FieldInfo(final String name, final String type, final String signature,
                         final List<String> annotations, final int access) {
            this.name = name;
            this.type = type;
            this.signature = signature;
            this.annotations = annotations;
            this.access = access;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getSignature() {
            return signature;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public int getAccess() {
            return access;
        }
    }

    public static final class MethodInfo {

        private final String name;
        private final String descriptor;
        private final String signature;
        private final List<String> annotations;
        private final List<String> parameterNames;
        private final List<String> declaredExceptions;
        private final List<String> thrownExceptions;
        private final int access;

        public MethodInfo(final String name, final String descriptor, final String signature,
                          final List<String> annotations, final List<String> parameterNames,
                          final List<String> declaredExceptions, final List<String> thrownExceptions,
                          final int access) {
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.annotations = annotations;
            this.parameterNames = parameterNames;
            this.declaredExceptions = declaredExceptions;
            this.thrownExceptions = thrownExceptions;
            this.access = access;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public String getSignature() {
            return signature;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public List<String> getParameterNames() {
            return parameterNames;
        }

        public List<String> getDeclaredExceptions() {
            return declaredExceptions;
        }

        public List<String> getThrownExceptions() {
            return thrownExceptions;
        }

        public int getAccess() {
            return access;
        }
    }
}
