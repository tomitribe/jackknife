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

    // ---- JSON output format ----

    @Test
    public void outputStartsWithJackknife() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        assertTrue("Should start with JACKKNIFE prefix", output().startsWith("JACKKNIFE "));
    }

    @Test
    public void outputContainsEventCall() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        assertTrue("Should contain event:call", output().contains("\"event\":\"call\""));
    }

    @Test
    public void outputContainsTime() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        assertTrue("Should contain time field", output().contains("\"time\":\""));
    }

    @Test
    public void outputContainsClassName() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        assertTrue("Should contain class name", output().contains("\"class\":\"SampleTarget\""));
    }

    @Test
    public void outputContainsMethodNameWithoutPrefix() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        final String out = output();
        assertTrue("Should contain method name", out.contains("\"method\":\"greet\""));
        assertTrue("Should NOT contain jackknife$ prefix", !out.contains("jackknife$"));
    }

    @Test
    public void argsFormattedAsJsonArray() throws Throwable {
        final DebugHandler handler = debugHandler(returning("result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"hello"});

        assertTrue("Should contain args as JSON array", output().contains("\"args\":[\"hello\"]"));
    }

    @Test
    public void returnValueInJson() throws Throwable {
        final DebugHandler handler = debugHandler(returning("the-result"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should contain return value", output().contains("\"return\":\"the-result\""));
    }

    @Test
    public void nullReturnValue() throws Throwable {
        final DebugHandler handler = debugHandler(returning(null));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should contain null return", output().contains("\"return\":null"));
    }

    @Test
    public void numericReturnValueBare() throws Throwable {
        final DebugHandler handler = debugHandler(returning(42));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should contain bare numeric", output().contains("\"return\":42"));
    }

    @Test
    public void booleanReturnValueBare() throws Throwable {
        final DebugHandler handler = debugHandler(returning(true));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should contain bare boolean", output().contains("\"return\":true"));
    }

    // ---- Exception output ----

    @Test
    public void exceptionInJson() throws Throwable {
        final DebugHandler handler = debugHandler(throwing(new IllegalArgumentException("bad input")));
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected exception");
        } catch (final IllegalArgumentException e) {
            // expected
        }

        final String out = output();
        assertTrue("Should contain exception type", out.contains("\"type\":\"java.lang.IllegalArgumentException\""));
        assertTrue("Should contain exception message", out.contains("\"message\":\"bad input\""));
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

    // ---- Static method ----

    @Test
    public void staticMethodWorks() throws Throwable {
        final Method staticMethod = SampleTarget.class.getDeclaredMethod("jackknife$multiply", long.class, long.class);
        final DebugHandler handler = debugHandler(returning(42L));
        handler.invoke(null, staticMethod, new Object[]{6L, 7L});

        final String out = output();
        assertTrue("Should contain JACKKNIFE prefix", out.startsWith("JACKKNIFE "));
        assertTrue("Should contain method name", out.contains("\"method\":\"multiply\""));
    }

    // ---- JSON value formatting ----

    @Test
    public void nullArgFormattedAsNull() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{null});

        assertTrue("Should format null arg as JSON null", output().contains("[null]"));
    }

    @Test
    public void noArgsFormattedAsEmptyArray() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, null);

        assertTrue("Should format empty args as []", output().contains("\"args\":[]"));
    }

    @Test
    public void objectArrayFormatted() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{new Object[]{"a", "b"}});

        assertTrue("Should format Object[] as JSON array", output().contains("[\"a\",\"b\"]"));
    }

    @Test
    public void byteArrayShowsLength() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{new byte[]{1, 2, 3, 4, 5}});

        assertTrue("Should show byte array length", output().contains("\"byte[5]\""));
    }

    @Test
    public void stringWithQuotesEscaped() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"say \"hello\""});

        assertTrue("Should escape quotes", output().contains("say \\\"hello\\\""));
    }

    // ---- Capture file behavior ----

    @Test
    public void shortValueStaysInline() throws Throwable {
        final DebugHandler handler = new DebugHandler(returning("short"), 500, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should contain return value inline", out.contains("\"return\":\"short\""));
        assertTrue("Should NOT have file reference", !out.contains("\"file\":"));
    }

    @Test
    public void valueOverThresholdWrittenToFile() throws Throwable {
        final String longValue = "x".repeat(600);
        final DebugHandler handler = new DebugHandler(returning(longValue), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should have file reference", out.contains("\"file\":\""));
        assertTrue("Should have status", out.contains("\"status\":\"returned\""));
    }

    @Test
    public void captureFileContainsFullJson() throws Throwable {
        final String longValue = "x".repeat(200);
        final DebugHandler handler = new DebugHandler(returning(longValue), 100, tmp.getRoot());
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final File[] captures = tmp.getRoot().listFiles((final File dir, final String name) -> name.startsWith("capture-"));
        assertTrue("Should have capture file", captures != null && captures.length > 0);
        final String content = Files.readString(captures[0].toPath());
        assertTrue("Capture file should contain full JSON event", content.contains("\"event\":\"call\""));
        assertTrue("Capture file should contain return value", content.contains(longValue));
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
    public void exceptionCaptureShowsExceptionType() throws Throwable {
        final DebugHandler handler = new DebugHandler(throwing(new RuntimeException("boom")), 100, tmp.getRoot());
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected exception");
        } catch (final RuntimeException e) {
            // expected
        }

        final String out = output();
        // May be inline or captured, either way should have exception info
        assertTrue("Should mention exception", out.contains("RuntimeException") || out.contains("\"status\":\"thrown\""));
    }

    @Test
    public void oneLinePerCall() throws Throwable {
        final DebugHandler handler = debugHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final long lineCount = output().lines().count();
        assertEquals("Should produce exactly one line per call", 1, lineCount);
    }

    // ---- Delegate ----

    @Test
    public void delegatesToNextHandler() throws Throwable {
        final InvocationHandler delegate = (final Object p, final Method m, final Object[] a) -> "delegated";
        final DebugHandler handler = debugHandler(delegate);
        final Object result = handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
        assertEquals("delegated", result);
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
