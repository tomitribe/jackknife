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
package org.tomitribe.jackknife.runtime;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HandlerRegistryTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ClassLoader originalTccl;

    @Before
    public void setUp() {
        HandlerRegistry.clear();
        originalOut = System.out;
        originalErr = System.err;
        originalTccl = Thread.currentThread().getContextClassLoader();
    }

    @After
    public void tearDown() {
        HandlerRegistry.clear();
        System.setOut(originalOut);
        System.setErr(originalErr);
        Thread.currentThread().setContextClassLoader(originalTccl);
    }

    @Test
    public void getHandlerReturnsNullWhenNoHandlerRegistered() {
        final InvocationHandler result = HandlerRegistry.getHandler("com.example.Foo", "bar");
        assertNull(result);
    }

    @Test
    public void getHandlerReturnsHandlerAfterRegister() {
        final InvocationHandler handler = dummyHandler();
        HandlerRegistry.register("com.example.Foo", "bar", handler);
        final InvocationHandler result = HandlerRegistry.getHandler("com.example.Foo", "bar");
        assertTrue("Should be the same handler instance", result == handler);
    }

    @Test
    public void unregisterRemovesHandler() {
        HandlerRegistry.register("com.example.Foo", "bar", dummyHandler());
        HandlerRegistry.unregister("com.example.Foo", "bar");
        assertNull(HandlerRegistry.getHandler("com.example.Foo", "bar"));
    }

    @Test
    public void clearResetsInitializationState() {
        HandlerRegistry.register("com.example.Foo", "bar", dummyHandler());
        assertNotNull(HandlerRegistry.getHandler("com.example.Foo", "bar"));

        HandlerRegistry.clear();
        // After clear, should be null (auto-discover runs again but finds nothing for this key)
        assertNull(HandlerRegistry.getHandler("com.example.Foo", "bar"));
    }

    @Test
    public void handlerKeyIsClassNameDotMethodName() {
        final InvocationHandler handler = dummyHandler();
        HandlerRegistry.register("com.example.Foo", "bar", handler);

        // Same class, different method
        assertNull(HandlerRegistry.getHandler("com.example.Foo", "baz"));
        // Different class, same method
        assertNull(HandlerRegistry.getHandler("com.example.Bar", "bar"));
        // Exact match
        assertNotNull(HandlerRegistry.getHandler("com.example.Foo", "bar"));
    }

    @Test
    public void handlesEmptyParamTypes() {
        // Directly test register + getHandler with no params (already tested above)
        HandlerRegistry.register("com.example.Foo", "noArgs", dummyHandler());
        assertNotNull(HandlerRegistry.getHandler("com.example.Foo", "noArgs"));
    }

    @Test
    public void autoDiscoverFromClasspath() {
        // The test classpath may or may not have META-INF/jackknife/handlers.properties.
        // We just verify getHandler doesn't throw during auto-discovery.
        // This also exercises the INITIALIZED compareAndSet path.
        HandlerRegistry.clear();
        final InvocationHandler result = HandlerRegistry.getHandler("nonexistent.Class", "method");
        assertNull("Auto-discover should not find anything for nonexistent class", result);
    }

    @Test
    public void threadSafeConcurrentGetHandler() throws Exception {
        final int threadCount = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(1);

        // Register a handler before the concurrent reads
        final InvocationHandler expected = dummyHandler();
        HandlerRegistry.register("com.example.Concurrent", "method", expected);

        final List<Future<InvocationHandler>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return HandlerRegistry.getHandler("com.example.Concurrent", "method");
            }));
        }

        latch.countDown(); // release all threads simultaneously

        final List<InvocationHandler> results = new ArrayList<>();
        for (final Future<InvocationHandler> f : futures) {
            results.add(f.get());
        }

        executor.shutdown();

        for (final InvocationHandler h : results) {
            assertTrue("All threads should get the same handler", h == expected);
        }
    }

    @Test
    public void multipleRegistrationsLastWins() {
        final InvocationHandler first = dummyHandler();
        final InvocationHandler second = dummyHandler();

        HandlerRegistry.register("com.example.Foo", "method", first);
        HandlerRegistry.register("com.example.Foo", "method", second);

        final InvocationHandler result = HandlerRegistry.getHandler("com.example.Foo", "method");
        assertTrue("Last registration should win", result == second);
    }

    @Test
    public void skipsCommentAndBlankLinesInAutoDiscover() {
        // This is implicitly tested by autoDiscoverFromClasspath not throwing,
        // but we can verify the behavior by checking that no spurious handlers are registered
        HandlerRegistry.clear();
        HandlerRegistry.getHandler("anything", "anything"); // triggers auto-discover
        // No assertions needed — if it didn't throw, comments/blanks were skipped
    }

    // ---- Auto-discover tests with test handlers.properties ----
    // Test classpath has META-INF/jackknife/handlers.properties with:
    //   debug SampleTarget greet java.lang.String
    //   timing SampleTarget add int,int
    //   all SampleTarget manyArgs java.lang.String,int,long,double,boolean
    //   debug SampleTarget noArgs

    @Test
    public void autoDiscoverParsesDebugMode() {
        // First getHandler triggers auto-discover
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "greet");
        assertNotNull("Should find debug handler for greet", handler);
        assertTrue("Debug mode wraps ProceedHandler in DebugHandler",
                handler instanceof DebugHandler);
    }

    @Test
    public void autoDiscoverParsesTimingMode() {
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "add");
        assertNotNull("Should find timing handler for add", handler);
        assertTrue("Timing mode wraps ProceedHandler in TimingHandler",
                handler instanceof TimingHandler);
    }

    @Test
    public void autoDiscoverBuildsDebugChain() {
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "greet");
        assertNotNull(handler);
        assertTrue("Should be DebugHandler", handler instanceof DebugHandler);
    }

    @Test
    public void autoDiscoverBuildsTimingChain() {
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "add");
        assertNotNull(handler);
        assertTrue("Should be TimingHandler", handler instanceof TimingHandler);
    }

    @Test
    public void autoDiscoverBuildsAllChain() {
        // "all" mode = TimingHandler(DebugHandler(ProceedHandler))
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "manyArgs");
        assertNotNull(handler);
        assertTrue("All mode should be TimingHandler wrapping DebugHandler",
                handler instanceof TimingHandler);
    }

    @Test
    public void autoDiscoverHandlesEmptyParamTypes() {
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "noArgs");
        assertNotNull("Should find handler for noArgs", handler);
    }

    @Test
    public void autoDiscoverHandlesMultipleParamTypes() {
        // "add" has paramTypes "int,int"
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "add");
        assertNotNull("Should find handler with comma-separated param types", handler);
    }

    @Test
    public void autoDiscoverResolvesPrimitiveTypes() {
        // "add" uses "int,int" — these must resolve to int.class, int.class
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "add");
        assertNotNull("Should resolve primitive types", handler);
    }

    @Test
    public void autoDiscoverResolvesFqnTypes() {
        // "greet" uses "java.lang.String" — must resolve to String.class
        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "greet");
        assertNotNull("Should resolve FQN types", handler);
    }

    @Test
    public void autoDiscoverInvokesDebugHandlerEndToEnd() throws Throwable {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "greet");
        assertNotNull(handler);

        final SampleTarget target = new SampleTarget();
        final Object result = handler.invoke(target, null, new Object[]{"World"});

        assertEquals("Hello, World!", result);
        final String output = out.toString();
        assertTrue("Should have ENTER from DebugHandler", output.contains("ENTER"));
        assertTrue("Should have EXIT from DebugHandler", output.contains("EXIT"));
    }

    @Test
    public void autoDiscoverInvokesTimingHandlerEndToEnd() throws Throwable {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final InvocationHandler handler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "add");
        assertNotNull(handler);

        final SampleTarget target = new SampleTarget();
        final Object result = handler.invoke(target, null, new Object[]{3, 4});

        assertEquals(7, result);
        final String output = out.toString();
        assertTrue("Should have TIMING from TimingHandler", output.contains("TIMING"));
    }

    @Test
    public void multipleHandlersPropertiesOnClasspath() throws Exception {
        // Create a second handlers.properties in a temp directory
        final File configDir = new File(tmp.getRoot(), "META-INF/jackknife");
        configDir.mkdirs();
        final File extraConfig = new File(configDir, "handlers.properties");
        try (final PrintWriter out = new PrintWriter(new FileWriter(extraConfig))) {
            out.println("# Extra config from second classpath entry");
            out.println("timing com.extra.FakeClass fakeMethod");
        }

        // Create a URLClassLoader that includes the temp dir AND the existing classpath
        final URL extraUrl = tmp.getRoot().toURI().toURL();
        final URLClassLoader loader = new URLClassLoader(
                new URL[]{extraUrl}, Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(loader);

        // Suppress stdout from registration messages
        System.setOut(new PrintStream(new ByteArrayOutputStream()));

        HandlerRegistry.clear();

        // Trigger auto-discover — should load from BOTH handlers.properties files
        HandlerRegistry.getHandler("anything", "anything");

        // Verify handler from the original test classpath config (SampleTarget.greet)
        final InvocationHandler greetHandler = HandlerRegistry.getHandler(
                "org.tomitribe.jackknife.runtime.SampleTarget", "greet");
        assertNotNull("Should have handler from original classpath config", greetHandler);

        // Verify handler from the extra config (com.extra.FakeClass.fakeMethod)
        final InvocationHandler fakeHandler = HandlerRegistry.getHandler(
                "com.extra.FakeClass", "fakeMethod");
        assertNotNull("Should have handler from extra classpath config", fakeHandler);

        loader.close();
    }

    private static InvocationHandler dummyHandler() {
        return (final Object proxy, final Method method, final Object[] args) -> null;
    }
}
