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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tomitribe.jackknife.runtime.DebugHandler;
import org.tomitribe.jackknife.runtime.HandlerRegistry;
import org.tomitribe.jackknife.runtime.ProceedHandler;
import org.tomitribe.jackknife.runtime.TimingHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * End-to-end tests: transform a class with HandlerEnhancer, load with a
 * custom classloader, register handler chains, invoke methods, and verify output.
 */
public class EndToEndTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private ClassLoader originalTccl;
    private String className;

    @Before
    public void setUp() {
        HandlerRegistry.clear();
        originalOut = System.out;
        originalTccl = Thread.currentThread().getContextClassLoader();
        System.setOut(new PrintStream(captured));
        className = SampleClass.class.getName();
    }

    @After
    public void tearDown() {
        HandlerRegistry.clear();
        System.setOut(originalOut);
        Thread.currentThread().setContextClassLoader(originalTccl);
    }

    private String output() {
        return captured.toString().trim();
    }

    // ---- Instance method end-to-end ----

    @Test
    public void debugModeInstanceMethod() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet"));
        registerDebug("greet", String.class);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("greet", String.class);
        final Object result = method.invoke(instance, "World");

        assertEquals("Hello, World!", result);
        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
        assertTrue("Should log EXIT", out.contains("EXIT"));
        assertTrue("Should contain arg", out.contains("World"));
        assertTrue("Should contain result", out.contains("Hello, World!"));
    }

    @Test
    public void timingModeInstanceMethod() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet"));
        registerTiming("greet", String.class);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("greet", String.class);
        final Object result = method.invoke(instance, "World");

        assertEquals("Hello, World!", result);
        final String out = output();
        assertTrue("Should log TIMING", out.contains("TIMING"));
    }

    // ---- Static method end-to-end ----

    @Test
    public void debugModeStaticMethod() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("multiply"));
        registerDebugStatic("multiply", long.class, long.class);

        final Method method = clazz.getMethod("multiply", long.class, long.class);
        final Object result = method.invoke(null, 6L, 7L);

        assertEquals(42L, result);
        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
        assertTrue("Should log EXIT", out.contains("EXIT"));
    }

    // ---- Void method end-to-end ----

    @Test
    public void debugModeVoidMethod() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("doWork"));
        registerDebug("doWork");

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("doWork");
        method.invoke(instance);

        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
        assertTrue("Should log EXIT", out.contains("EXIT"));
    }

    // ---- Primitive return end-to-end ----

    @Test
    public void debugModePrimitiveReturn() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("add"));
        registerDebug("add", int.class, int.class);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("add", int.class, int.class);
        final Object result = method.invoke(instance, 3, 4);

        assertEquals(7, result);
        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
        assertTrue("Should log EXIT", out.contains("EXIT"));
    }

    // ---- Exception end-to-end ----

    @Test
    public void debugModeExceptionPropagates() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("throwsUnchecked"));
        registerDebug("throwsUnchecked", String.class);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("throwsUnchecked", String.class);
        try {
            method.invoke(instance, "test");
            fail("Expected exception");
        } catch (final InvocationTargetException e) {
            assertTrue("Should be IllegalArgumentException",
                    e.getCause() instanceof IllegalArgumentException);
            assertEquals("unchecked: test", e.getCause().getMessage());
        }

        final String out = output();
        assertTrue("Should log THROW", out.contains("THROW"));
        assertTrue("Should contain exception class", out.contains("IllegalArgumentException"));
    }

    // ---- Combined chain end-to-end ----

    @Test
    public void combinedTimingAndDebugChain() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet"));

        // Chain: TimingHandler(DebugHandler(ProceedHandler))
        final ProceedHandler proceed = new ProceedHandler(className, "greet", new Class<?>[]{String.class});
        final DebugHandler debug = new DebugHandler(proceed, 500, tmp.getRoot());
        final TimingHandler timing = new TimingHandler(debug);
        HandlerRegistry.register(className, "greet", timing);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("greet", String.class);
        final Object result = method.invoke(instance, "World");

        assertEquals("Hello, World!", result);
        final String out = output();
        assertTrue("Should log ENTER from DebugHandler", out.contains("ENTER"));
        assertTrue("Should log EXIT from DebugHandler", out.contains("EXIT"));
        assertTrue("Should log TIMING from TimingHandler", out.contains("TIMING"));
    }

    // ---- No handler registered — fallback ----

    @Test
    public void noHandlerFallbackToDirectCall() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet"));
        // Don't register any handler

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("greet", String.class);
        final Object result = method.invoke(instance, "World");

        assertEquals("Hello, World!", result);
        final String out = output();
        // No debug/timing output since no handler registered
        assertTrue("Should have no ENTER/EXIT/TIMING output",
                !out.contains("ENTER") && !out.contains("TIMING"));
    }

    // ---- Large return value capture ----

    @Test
    public void largeReturnValueCaptured() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet"));

        final File capturesDir = tmp.newFolder("captures");
        final ProceedHandler proceed = new ProceedHandler(className, "greet", new Class<?>[]{String.class});
        final DebugHandler debug = new DebugHandler(proceed, 10, capturesDir);
        HandlerRegistry.register(className, "greet", debug);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method method = clazz.getMethod("greet", String.class);
        method.invoke(instance, "World");

        final String out = output();
        assertTrue("Should reference capture file for return value", out.contains("[file:"));

        final File[] captures = capturesDir.listFiles((final File dir, final String name) -> name.startsWith("capture-"));
        assertTrue("Should have capture files", captures != null && captures.length > 0);
    }

    // ---- Multiple methods in same class ----

    @Test
    public void multipleMethodsInSameClass() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("greet", "add", "doWork"));
        registerDebug("greet", String.class);
        registerDebug("add", int.class, int.class);
        registerDebug("doWork");

        final Object instance = clazz.getDeclaredConstructor().newInstance();

        final Method greet = clazz.getMethod("greet", String.class);
        assertEquals("Hello, World!", greet.invoke(instance, "World"));

        final Method add = clazz.getMethod("add", int.class, int.class);
        assertEquals(7, add.invoke(instance, 3, 4));

        final Method doWork = clazz.getMethod("doWork");
        doWork.invoke(instance);

        final String out = output();
        // Should have ENTER/EXIT for each method
        final long enterCount = out.lines().filter(l -> l.contains("ENTER")).count();
        assertTrue("Should have 3 ENTER lines", enterCount >= 3);
    }

    // ---- Overloaded methods ----

    @Test
    public void overloadedMethodsAllInstrumented() throws Exception {
        final Class<?> clazz = transformAndLoad(Set.of("overloaded"));

        // Register handler for overloaded(String) variant
        registerDebug("overloaded", String.class);

        final Object instance = clazz.getDeclaredConstructor().newInstance();
        final Method oneArg = clazz.getMethod("overloaded", String.class);
        final Object result = oneArg.invoke(instance, "a");
        assertEquals("one:a", result);

        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
    }

    // ---- Helper methods ----

    private Class<?> transformAndLoad(final Set<String> methods) throws IOException, ClassNotFoundException {
        final byte[] original = readClassBytes(SampleClass.class);
        final byte[] transformed = HandlerEnhancer.enhance(original, methods);

        final ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, transformed, 0, transformed.length);
                }
                throw new ClassNotFoundException(name);
            }

            @Override
            public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return findClass(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        // Set TCCL so ProceedHandler can resolve static method target classes
        Thread.currentThread().setContextClassLoader(loader);

        return loader.loadClass(className);
    }

    private void registerDebug(final String methodName, final Class<?>... paramTypes) {
        final ProceedHandler proceed = new ProceedHandler(className, methodName, paramTypes);
        final DebugHandler debug = new DebugHandler(proceed, 500, tmp.getRoot());
        HandlerRegistry.register(className, methodName, debug);
    }

    private void registerDebugStatic(final String methodName, final Class<?>... paramTypes) {
        final ProceedHandler proceed = new ProceedHandler(className, methodName, paramTypes);
        final DebugHandler debug = new DebugHandler(proceed, 500, tmp.getRoot());
        HandlerRegistry.register(className, methodName, debug);
    }

    private void registerTiming(final String methodName, final Class<?>... paramTypes) {
        final ProceedHandler proceed = new ProceedHandler(className, methodName, paramTypes);
        final TimingHandler timing = new TimingHandler(proceed);
        HandlerRegistry.register(className, methodName, timing);
    }

    private static byte[] readClassBytes(final Class<?> clazz) throws IOException {
        final String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (final InputStream is = clazz.getResourceAsStream(resource)) {
            assertNotNull("Class resource not found: " + resource, is);
            return is.readAllBytes();
        }
    }
}
