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
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DebugHandlerTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;

    /** A sample renamed method — simulates what HandlerEnhancer produces */
    private final Method sampleMethod;

    public DebugHandlerTest() throws NoSuchMethodException {
        sampleMethod = SampleTarget.class.getDeclaredMethod("jackknife$greet", String.class);
        sampleMethod.setAccessible(true);
    }

    @Before
    public void captureStdout() {
        originalOut = System.out;
        System.setOut(new PrintStream(captured));
    }

    @After
    public void restoreStdout() {
        System.setOut(originalOut);
    }

    private String output() {
        return captured.toString().trim();
    }

    private DebugHandler debugHandler(final InvocationHandler delegate) {
        return new DebugHandler(delegate, 500, tmp.getRoot());
    }

    // ---- ENTER/EXIT logging ----

    @Test
    public void logsEnterWithArgsForInstanceMethod() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        final String out = output();
        assertTrue("Should log ENTER", out.contains("ENTER"));
        assertTrue("Should contain args", out.contains("hello"));
    }

    @Test
    public void logsEnterWithArgsForStaticMethod() throws Throwable {
        final Method staticMethod = SampleTarget.class.getDeclaredMethod("jackknife$multiply", long.class, long.class);
        final DebugHandler handler = debugHandler(returning(42L));
        handler.invoke(null, staticMethod, new Object[]{6L, 7L});

        final String out = output();
        assertTrue("Should log ENTER without NPE", out.contains("ENTER"));
        assertTrue("Should log EXIT", out.contains("EXIT"));
    }

    @Test
    public void logsExitWithReturnValue() throws Throwable {
        final DebugHandler handler = debugHandler(returning("the-result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should log EXIT with return value", out.contains("EXIT"));
        assertTrue("Should contain return value", out.contains("the-result"));
    }

    @Test
    public void logsExitWithNullReturnValue() throws Throwable {
        final DebugHandler handler = debugHandler(returning(null));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should log EXIT", out.contains("EXIT"));
        assertTrue("Should contain null", out.contains("null"));
    }

    @Test
    public void logsExitWithPrimitiveReturnValue() throws Throwable {
        final DebugHandler handler = debugHandler(returning(42));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should contain boxed primitive", out.contains("42"));
    }

    @Test
    public void logsThrowWithExceptionClassAndMessage() throws Throwable {
        final DebugHandler handler = debugHandler(throwing(new IllegalArgumentException("bad input")));
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected exception");
        } catch (final IllegalArgumentException e) {
            // expected
        }

        final String out = output();
        assertTrue("Should log THROW", out.contains("THROW"));
        assertTrue("Should contain exception class", out.contains("IllegalArgumentException"));
        assertTrue("Should contain message", out.contains("bad input"));
    }

    @Test
    public void rethrowsOriginalException() throws Throwable {
        final IllegalStateException original = new IllegalStateException("original");
        final DebugHandler handler = debugHandler(throwing(original));
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected exception");
        } catch (final IllegalStateException e) {
            assertTrue("Should be the exact same exception", e == original);
        }
    }

    @Test
    public void delegatesToNextHandler() throws Throwable {
        final InvocationHandler delegate = (final Object p, final Method m, final Object[] a) -> "delegated";
        final DebugHandler handler = debugHandler(delegate);
        final Object result = handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
        assertEquals("delegated", result);
    }

    // ---- Label formatting ----

    @Test
    public void labelShowsClassAndMethodName() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should show class name", out.contains("SampleTarget"));
        assertTrue("Should show method name without prefix", out.contains("greet"));
        assertTrue("Should NOT show jackknife$ prefix", !out.contains("jackknife$"));
    }

    // ---- Array formatting ----

    @Test
    public void objectArrayFormatted() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{new Object[]{"a", "b"}});

        final String out = output();
        assertTrue("Should format Object[] with deepToString", out.contains("[a, b]"));
    }

    @Test
    public void intArrayFormatted() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{new int[]{1, 2, 3}});

        final String out = output();
        assertTrue("Should format int[]", out.contains("[1, 2, 3]"));
    }

    @Test
    public void byteArrayShowsLength() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{new byte[]{1, 2, 3, 4, 5}});

        final String out = output();
        assertTrue("Should show byte array length", out.contains("byte[5]"));
    }

    @Test
    public void nullArgFormattedAsNull() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{null});

        final String out = output();
        assertTrue("Should format null arg", out.contains("null"));
    }

    @Test
    public void noArgsFormatted() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, null);

        final String out = output();
        assertTrue("Should format empty args as ()", out.contains("()"));
    }

    // ---- Capture file behavior ----

    @Test
    public void shortValueStaysInline() throws Throwable {
        final DebugHandler handler = new DebugHandler(returning("short"), 500, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Short value should be inline", out.contains("short"));
        final File[] captures = tmp.getRoot().listFiles((final File dir, final String name) -> name.startsWith("capture-"));
        assertTrue("No capture file for short values", captures == null || captures.length == 0);
    }

    @Test
    public void valueOverThresholdWrittenToFile() throws Throwable {
        final String longValue = "x".repeat(600);
        final DebugHandler handler = new DebugHandler(returning(longValue), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should reference capture file", out.contains("[file:"));
    }

    @Test
    public void shortValueWithNewlinesGoesToFile() throws Throwable {
        final DebugHandler handler = new DebugHandler(returning("line1\nline2"), 500, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Newline value should go to file", out.contains("[file:"));
    }

    @Test
    public void captureFileContentMatchesFullValue() throws Throwable {
        final String longValue = "x".repeat(200);
        final DebugHandler handler = new DebugHandler(returning(longValue), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final File[] captures = tmp.getRoot().listFiles((final File dir, final String name) -> name.startsWith("capture-"));
        assertTrue("Should have capture file", captures != null && captures.length > 0);
        final String content = Files.readString(captures[0].toPath());
        assertEquals("Capture file should contain full value", longValue, content);
    }

    @Test
    public void captureFileReferenceIsParseable() throws Throwable {
        final String longValue = "x".repeat(200);
        final DebugHandler handler = new DebugHandler(returning(longValue), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should have [file: prefix", out.contains("[file: "));
        assertTrue("Should have .txt suffix in reference", out.contains(".txt]"));
    }

    @Test
    public void captureDirectoryCreatedIfNotExists() throws Throwable {
        final File subDir = new File(tmp.getRoot(), "nested/captures");
        final DebugHandler handler = new DebugHandler(returning("x".repeat(200)), 100, subDir);
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Captures directory should be created", subDir.exists());
    }

    @Test
    public void captureFileNamingIsSequential() throws Throwable {
        final DebugHandler handler = new DebugHandler(returning("x".repeat(200)), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final File[] captures = tmp.getRoot().listFiles((final File dir, final String name) -> name.startsWith("capture-"));
        assertTrue("Should have multiple capture files", captures != null && captures.length >= 2);
    }

    @Test
    public void largeArgsGoToFile() throws Throwable {
        final String bigArg = "a".repeat(600);
        final DebugHandler handler = new DebugHandler(returning("ok"), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{bigArg});

        final String out = output();
        assertTrue("Large args should trigger capture", out.contains("[file:"));
    }

    // ---- Helpers ----

    private static InvocationHandler returning(final Object value) {
        return (final Object proxy, final Method method, final Object[] args) -> value;
    }

    private static InvocationHandler throwing(final Throwable t) {
        return (final Object proxy, final Method method, final Object[] args) -> {
            throw t;
        };
    }
}
