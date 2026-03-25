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
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TimingHandlerTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private final Method sampleMethod;

    public TimingHandlerTest() throws NoSuchMethodException {
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

    @Test
    public void logsTimingWithElapsedTime() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should log TIMING", out.contains("TIMING"));
    }

    @Test
    public void formatsNanosecondsCorrectly() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should have time unit",
                out.contains("ns") || out.contains("us") || out.contains("ms")
                        || out.matches(".*\\d+\\.\\d+s.*"));
    }

    @Test
    public void staticMethodNullProxy() throws Throwable {
        final Method staticMethod = SampleTarget.class.getDeclaredMethod("jackknife$multiply", long.class, long.class);
        final TimingHandler handler = new TimingHandler(returning(42L));
        handler.invoke(null, staticMethod, new Object[]{6L, 7L});

        final String out = output();
        assertTrue("Should log TIMING without NPE", out.contains("TIMING"));
    }

    @Test
    public void delegatesToNextHandler() throws Throwable {
        final InvocationHandler delegate = (final Object p, final Method m, final Object[] a) -> "delegated";
        final TimingHandler handler = new TimingHandler(delegate);
        final Object result = handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
        assertEquals("delegated", result);
    }

    @Test
    public void exceptionInDelegateStillLogsTiming() throws Throwable {
        final TimingHandler handler = new TimingHandler(throwing(new RuntimeException("boom")));
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected RuntimeException");
        } catch (final RuntimeException e) {
            assertEquals("boom", e.getMessage());
        }

        final String out = output();
        assertTrue("Should still log TIMING after exception", out.contains("TIMING"));
    }

    @Test
    public void labelShowsClassAndMethodName() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should show class name", out.contains("SampleTarget"));
        assertTrue("Should show method name without prefix", out.contains("greet"));
        assertTrue("Should NOT show jackknife$ prefix", !out.contains("jackknife$"));
    }

    private static InvocationHandler returning(final Object value) {
        return (final Object proxy, final Method method, final Object[] args) -> value;
    }

    private static InvocationHandler throwing(final Throwable t) {
        return (final Object proxy, final Method method, final Object[] args) -> {
            throw t;
        };
    }
}
