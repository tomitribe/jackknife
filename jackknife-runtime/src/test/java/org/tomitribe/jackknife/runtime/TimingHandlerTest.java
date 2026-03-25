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
    public void outputIsJsonWithJacknifePrefix() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should start with JACKKNIFE", out.startsWith("JACKKNIFE "));
        assertTrue("Should be JSON", out.contains("\"event\":\"call\""));
    }

    @Test
    public void containsTimeField() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should contain time", output().contains("\"time\":\""));
    }

    @Test
    public void containsMethodInfo() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should contain class", out.contains("\"class\":\"SampleTarget\""));
        assertTrue("Should contain method", out.contains("\"method\":\"greet\""));
    }

    @Test
    public void noArgsOrReturnFields() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        final String out = output();
        assertTrue("Should NOT contain args", !out.contains("\"args\""));
        assertTrue("Should NOT contain return", !out.contains("\"return\""));
    }

    @Test
    public void statusReturnedOnSuccess() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertTrue("Should have status returned", output().contains("\"status\":\"returned\""));
    }

    @Test
    public void statusThrownOnException() throws Throwable {
        final TimingHandler handler = new TimingHandler(throwing(new RuntimeException("boom")));
        try {
            handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
            fail("Expected RuntimeException");
        } catch (final RuntimeException e) {
            assertEquals("boom", e.getMessage());
        }

        assertTrue("Should have status thrown", output().contains("\"status\":\"thrown\""));
    }

    @Test
    public void staticMethodWorks() throws Throwable {
        final Method staticMethod = SampleTarget.class.getDeclaredMethod("jackknife$multiply", long.class, long.class);
        final TimingHandler handler = new TimingHandler(returning(42L));
        handler.invoke(null, staticMethod, new Object[]{6L, 7L});

        assertTrue("Should work without NPE", output().contains("\"method\":\"multiply\""));
    }

    @Test
    public void delegatesToNextHandler() throws Throwable {
        final InvocationHandler delegate = (final Object p, final Method m, final Object[] a) -> "delegated";
        final TimingHandler handler = new TimingHandler(delegate);
        final Object result = handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});
        assertEquals("delegated", result);
    }

    @Test
    public void oneLinePerCall() throws Throwable {
        final TimingHandler handler = new TimingHandler(returning("ok"));
        handler.invoke(new SampleTarget(), sampleMethod, new Object[]{"x"});

        assertEquals("Should produce exactly one line", 1, output().lines().count());
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
