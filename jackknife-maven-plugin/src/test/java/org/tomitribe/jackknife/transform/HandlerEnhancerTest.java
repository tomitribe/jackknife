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

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests bytecode transformation by decompiling the result with Vineflower.
 * Follows the Snitch testing pattern but uses decompilation instead of ASMifier
 * for human-readable verification.
 */
public class HandlerEnhancerTest {

    @Test
    public void testTransformAndDecompile() throws IOException {
        // Read the original class bytes
        final byte[] original = readClassBytes(SampleClass.class);

        // Transform: wrap greet, add, doWork, multiply, check
        final byte[] transformed = HandlerEnhancer.enhance(original, Set.of("greet", "add", "doWork", "multiply", "check"));

        // Decompile the transformed bytes
        final String decompiled = decompile(transformed, SampleClass.class.getName());

        assertNotNull("Decompiled output should not be null", decompiled);

        System.out.println("=== Decompiled transformed class ===");
        System.out.println(decompiled);
        System.out.println("====================================");

        // Verify the wrapper methods exist
        assertTrue("Should contain wrapper for greet", decompiled.contains("public String greet("));
        assertTrue("Should contain renamed original", decompiled.contains("jackknife$greet"));
        assertTrue("Should contain wrapper for add", decompiled.contains("public int add("));
        assertTrue("Should contain renamed add", decompiled.contains("jackknife$add"));
        assertTrue("Should contain wrapper for doWork", decompiled.contains("public void doWork("));
        assertTrue("Should contain renamed doWork", decompiled.contains("jackknife$doWork"));
        assertTrue("Should contain static wrapper for multiply", decompiled.contains("public static long multiply("));
        assertTrue("Should contain renamed multiply", decompiled.contains("jackknife$multiply"));

        // Verify InvocationHandler delegation
        assertTrue("Should call HandlerRegistry.getHandler", decompiled.contains("HandlerRegistry.getHandler"));
        assertTrue("Should call handler.invoke", decompiled.contains(".invoke("));
    }

    private static byte[] readClassBytes(final Class<?> clazz) throws IOException {
        final String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (final InputStream is = clazz.getResourceAsStream(resource)) {
            assertNotNull("Class resource not found: " + resource, is);
            return is.readAllBytes();
        }
    }

    private static String decompile(final byte[] classBytes, final String className) {
        final String internalName = className.replace('.', '/');
        final AtomicReference<String> result = new AtomicReference<>();

        final IContextSource source = new IContextSource() {
            @Override
            public String getName() {
                return "test";
            }

            @Override
            public Entries getEntries() {
                return new Entries(
                        List.of(IContextSource.Entry.atBase(internalName)),
                        List.of(),
                        List.of()
                );
            }

            @Override
            public byte[] getClassBytes(final String name) {
                if (name.equals(internalName)) {
                    return classBytes;
                }
                return null;
            }

            @Override
            public InputStream getInputStream(final String resource) {
                if (resource.equals(internalName + ".class")) {
                    return new ByteArrayInputStream(classBytes);
                }
                return null;
            }

            @Override
            public IOutputSink createOutputSink(final IResultSaver saver) {
                return new IOutputSink() {
                    @Override
                    public void begin() {
                    }

                    @Override
                    public void acceptClass(final String qualifiedName, final String fileName,
                                            final String content, final int[] mapping) {
                        if (content != null) {
                            result.set(content);
                        }
                    }

                    @Override
                    public void acceptDirectory(final String directory) {
                    }

                    @Override
                    public void acceptOther(final String path) {
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };

        final IResultSaver saver = new IResultSaver() {
            @Override
            public void saveClassFile(final String path, final String qualifiedName,
                                      final String entryName, final String content, final int[] mapping) {
                if (content != null) {
                    result.set(content);
                }
            }

            @Override
            public void saveClassEntry(final String path, final String archiveName,
                                       final String qualifiedName, final String entryName, final String content) {
                if (content != null) {
                    result.set(content);
                }
            }

            @Override
            public void saveFolder(final String path) {
            }

            @Override
            public void copyFile(final String source, final String path, final String entryName) {
            }

            @Override
            public void createArchive(final String path, final String archiveName, final Manifest manifest) {
            }

            @Override
            public void saveDirEntry(final String path, final String archiveName, final String entryName) {
            }

            @Override
            public void copyEntry(final String source, final String path, final String archiveName, final String entry) {
            }

            @Override
            public void closeArchive(final String path, final String archiveName) {
            }
        };

        final Decompiler decompiler = Decompiler.builder()
                .inputs(source)
                .output(saver)
                .option(IFernflowerPreferences.LOG_LEVEL, "error")
                .option(IFernflowerPreferences.THREADS, "1")
                .option(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, true)
                .option(IFernflowerPreferences.REMOVE_BRIDGE, true)
                .option(IFernflowerPreferences.REMOVE_SYNTHETIC, true)
                .build();

        decompiler.decompile();

        return result.get();
    }
}
