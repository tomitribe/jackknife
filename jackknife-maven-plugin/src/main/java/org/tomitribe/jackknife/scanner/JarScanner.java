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

import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans a jar file in a single pass, extracting structural metadata for all classes
 * and a list of non-class resources.
 */
public final class JarScanner {

    private JarScanner() {
    }

    public static Result scan(final File jarFile) throws IOException {
        final List<ClassStructure> classes = new ArrayList<>();
        final List<String> resources = new ArrayList<>();

        try (final JarFile jar = new JarFile(jarFile)) {
            final var entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String name = entry.getName();

                if (entry.isDirectory()) {
                    continue;
                }

                if (name.endsWith(".class")) {
                    // Skip module-info and package-info
                    if (name.endsWith("module-info.class") || name.endsWith("package-info.class")) {
                        continue;
                    }

                    try (final InputStream is = jar.getInputStream(entry)) {
                        final ClassReader reader = new ClassReader(is);
                        final StructureVisitor visitor = new StructureVisitor();
                        reader.accept(visitor, 0); // no flags — full read
                        classes.add(visitor.build());
                    } catch (final Exception e) {
                        // Skip classes we can't read — malformed bytecode, etc.
                    }
                } else {
                    resources.add(name);
                }
            }
        }

        return new Result(classes, resources);
    }

    public static final class Result {

        private final List<ClassStructure> classes;
        private final List<String> resources;

        public Result(final List<ClassStructure> classes, final List<String> resources) {
            this.classes = classes;
            this.resources = resources;
        }

        public List<ClassStructure> getClasses() {
            return classes;
        }

        public List<String> getResources() {
            return resources;
        }
    }
}
