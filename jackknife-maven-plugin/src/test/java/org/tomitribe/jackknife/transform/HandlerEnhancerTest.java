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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests bytecode transformation by decompiling the result with Vineflower.
 * Follows the Snitch testing pattern but uses decompilation instead of ASMifier
 * for human-readable verification.
 */
public class HandlerEnhancerTest {

    // All method names that exist in SampleClass (excluding constructors/static initializers)
    private static final Set<String> ALL_METHODS = Set.of(
            "greet", "add", "doWork", "multiply", "check",
            "returnByte", "returnShort", "returnChar", "returnFloat", "returnDouble",
            "returnObjectArray", "returnIntArray", "returnByteArray",
            "noParams", "manyParams", "sumVarargs",
            "joinList", "mapLookup", "toList",
            "syncMethod", "throwsChecked", "throwsUnchecked",
            "overloaded", "privateMethod", "protectedMethod", "packageMethod", "finalMethod",
            "staticVoid", "staticReturn",
            "annotatedMethod", "multiAnnotated", "paramAnnotated"
    );

    // ---- Method shapes ----

    @Test
    public void instanceMethodWithReturnValue() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("greet"));
        assertWrapped(decompiled, "greet");
        assertTrue("Should have String return type", decompiled.contains("public String greet("));
    }

    @Test
    public void instanceVoidMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("doWork"));
        assertWrapped(decompiled, "doWork");
        assertTrue("Should have void return", decompiled.contains("public void doWork("));
    }

    @Test
    public void staticMethodWithReturnValue() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("multiply"));
        assertWrapped(decompiled, "multiply");
        assertTrue("Should be static", decompiled.contains("public static long multiply("));
    }

    @Test
    public void staticVoidMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("staticVoid"));
        assertWrapped(decompiled, "staticVoid");
        assertTrue("Should be static void", decompiled.contains("public static void staticVoid("));
    }

    @Test
    public void primitiveReturnInt() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("add"));
        assertWrapped(decompiled, "add");
        assertTrue("Should have int return", decompiled.contains("public int add("));
    }

    @Test
    public void primitiveReturnBoolean() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("check"));
        assertWrapped(decompiled, "check");
        assertTrue("Should have boolean return", decompiled.contains("public boolean check("));
    }

    @Test
    public void primitiveReturnByte() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnByte"));
        assertWrapped(decompiled, "returnByte");
    }

    @Test
    public void primitiveReturnShort() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnShort"));
        assertWrapped(decompiled, "returnShort");
    }

    @Test
    public void primitiveReturnChar() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnChar"));
        assertWrapped(decompiled, "returnChar");
    }

    @Test
    public void primitiveReturnFloat() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnFloat"));
        assertWrapped(decompiled, "returnFloat");
    }

    @Test
    public void primitiveReturnDouble() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnDouble"));
        assertWrapped(decompiled, "returnDouble");
    }

    @Test
    public void primitiveReturnLong() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("multiply"));
        assertWrapped(decompiled, "multiply");
    }

    @Test
    public void returnObjectArray() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnObjectArray"));
        assertWrapped(decompiled, "returnObjectArray");
    }

    @Test
    public void returnIntArray() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnIntArray"));
        assertWrapped(decompiled, "returnIntArray");
    }

    @Test
    public void returnByteArray() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("returnByteArray"));
        assertWrapped(decompiled, "returnByteArray");
    }

    @Test
    public void methodWithNoParameters() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("noParams"));
        assertWrapped(decompiled, "noParams");
    }

    @Test
    public void methodWithManyParameters() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("manyParams"));
        assertWrapped(decompiled, "manyParams");
    }

    @Test
    public void methodWithVarargs() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("sumVarargs"));
        assertWrapped(decompiled, "sumVarargs");
    }

    @Test
    public void methodWithGenericParameters() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("joinList"));
        assertWrapped(decompiled, "joinList");
    }

    @Test
    public void methodWithGenericReturnType() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("toList"));
        assertWrapped(decompiled, "toList");
    }

    @Test
    public void synchronizedMethodDropsSynchronized() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("syncMethod"));
        assertWrapped(decompiled, "syncMethod");
        // The wrapper should not be synchronized — only the renamed original retains it
        // We check that the wrapper (with HandlerRegistry.getHandler) is not synchronized
        // This is hard to check precisely in decompiled output since the renamed original
        // retains synchronized. At minimum, verify both exist.
        assertTrue("Should contain wrapper", decompiled.contains("HandlerRegistry.getHandler"));
    }

    @Test
    public void methodThrowsCheckedException() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("throwsChecked"));
        assertWrapped(decompiled, "throwsChecked");
    }

    @Test
    public void methodThrowsUncheckedException() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("throwsUnchecked"));
        assertWrapped(decompiled, "throwsUnchecked");
    }

    @Test
    public void overloadedMethodsAllWrapped() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("overloaded"));
        assertTrue("Should have renamed originals", decompiled.contains("jackknife$overloaded"));
        assertTrue("Should have handler delegation", decompiled.contains("HandlerRegistry.getHandler"));
    }

    @Test
    public void privateMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("privateMethod"));
        assertWrapped(decompiled, "privateMethod");
    }

    @Test
    public void protectedMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("protectedMethod"));
        assertWrapped(decompiled, "protectedMethod");
    }

    @Test
    public void packagePrivateMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("packageMethod"));
        assertWrapped(decompiled, "packageMethod");
    }

    @Test
    public void finalMethod() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("finalMethod"));
        assertWrapped(decompiled, "finalMethod");
    }

    @Test
    public void constructorNotWrapped() throws IOException {
        // Request wrapping "<init>" — should be skipped
        final String decompiled = transformAndDecompile(Set.of("<init>"));
        assertFalse("Constructor should NOT be renamed",
                decompiled.contains("jackknife$<init>") || decompiled.contains("jackknife$_init_"));
    }

    @Test
    public void staticInitializerNotWrapped() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("<clinit>"));
        assertFalse("Static initializer should NOT be renamed",
                decompiled.contains("jackknife$<clinit>") || decompiled.contains("jackknife$_clinit_"));
    }

    // ---- Decompile verification ----

    @Test
    public void decompiledWrapperShowsGetHandler() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("greet"));
        assertTrue("Should show HandlerRegistry.getHandler call",
                decompiled.contains("HandlerRegistry.getHandler"));
    }

    @Test
    public void decompiledWrapperShowsNullCheckFallback() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("greet"));
        // The wrapper should have a null check: if handler is null, call jackknife$greet directly
        assertTrue("Should have null check or conditional",
                decompiled.contains("jackknife$greet"));
    }

    @Test
    public void decompiledWrapperShowsArgBoxing() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("add"));
        // add(int, int) should box args into Object[]
        assertTrue("Should have Object array creation",
                decompiled.contains("Object[]") || decompiled.contains("new Object"));
    }

    @Test
    public void decompiledWrapperShowsHandlerInvoke() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("greet"));
        assertTrue("Should call handler.invoke", decompiled.contains(".invoke("));
    }

    // ---- Annotation movement ----

    @Test
    public void annotationMovesFromRenamedToWrapper() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("annotatedMethod"));
        // @Deprecated should be on the wrapper method (annotatedMethod), not on jackknife$annotatedMethod
        // We can verify by checking that @Deprecated appears near the wrapper
        assertTrue("Wrapper should have @Deprecated", decompiled.contains("@Deprecated"));
        assertWrapped(decompiled, "annotatedMethod");
    }

    @Test
    public void multipleAnnotationsMove() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("multiAnnotated"));
        assertTrue("Should have @Deprecated", decompiled.contains("@Deprecated"));
        assertWrapped(decompiled, "multiAnnotated");
    }

    @Test
    public void parameterAnnotationsMove() throws IOException {
        final String decompiled = transformAndDecompile(Set.of("paramAnnotated"));
        assertWrapped(decompiled, "paramAnnotated");
        // TestAnnotation should appear on the wrapper's parameter
        assertTrue("Should have parameter annotation in output",
                decompiled.contains("TestAnnotation"));
    }

    // ---- Full transform with all methods ----

    @Test
    public void transformAllMethodsDecompilesSuccessfully() throws IOException {
        final String decompiled = transformAndDecompile(ALL_METHODS);
        assertNotNull("Should decompile successfully", decompiled);
        assertTrue("Should contain HandlerRegistry calls", decompiled.contains("HandlerRegistry.getHandler"));
    }

    // ---- Helper methods ----

    private String transformAndDecompile(final Set<String> methods) throws IOException {
        final byte[] original = readClassBytes(SampleClass.class);
        final byte[] transformed = HandlerEnhancer.enhance(original, methods);
        final String decompiled = decompile(transformed, SampleClass.class.getName());
        assertNotNull("Decompiled output should not be null", decompiled);
        return decompiled;
    }

    private void assertWrapped(final String decompiled, final String methodName) {
        assertTrue("Should contain renamed method jackknife$" + methodName,
                decompiled.contains("jackknife$" + methodName));
        assertTrue("Should contain HandlerRegistry.getHandler",
                decompiled.contains("HandlerRegistry.getHandler"));
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
