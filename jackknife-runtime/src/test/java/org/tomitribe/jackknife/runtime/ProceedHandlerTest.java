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

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProceedHandlerTest {

    private final SampleTarget target = new SampleTarget();

    @Test
    public void instanceMethodWithReturnValue() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("greet", new Class<?>[]{String.class});
        final Object result = handler.invoke(target, null, new Object[]{"World"});
        assertEquals("Hello, World!", result);
    }

    @Test
    public void instanceVoidMethod() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("doWork", new Class<?>[0]);
        final Object result = handler.invoke(target, null, new Object[0]);
        assertNull(result);
    }

    @Test
    public void staticMethodWithReturnValue() throws Throwable {
        final ProceedHandler handler = new ProceedHandler(
                SampleTarget.class.getName(), "multiply", new Class<?>[]{long.class, long.class});
        final Object result = handler.invoke(null, null, new Object[]{6L, 7L});
        assertEquals(42L, result);
    }

    @Test
    public void staticVoidMethod() throws Throwable {
        final ProceedHandler handler = new ProceedHandler(
                SampleTarget.class.getName(), "staticVoid", new Class<?>[0]);
        final Object result = handler.invoke(null, null, new Object[0]);
        assertNull(result);
    }

    @Test
    public void primitiveReturnInt() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("add", new Class<?>[]{int.class, int.class});
        final Object result = handler.invoke(target, null, new Object[]{3, 4});
        assertEquals(7, result);
    }

    @Test
    public void primitiveReturnBoolean() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("check", new Class<?>[]{String.class, int.class});
        final Object result = handler.invoke(target, null, new Object[]{"hello", 3});
        assertEquals(true, result);
    }

    @Test
    public void primitiveReturnLong() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("computeLong", new Class<?>[]{long.class});
        final Object result = handler.invoke(target, null, new Object[]{21L});
        assertEquals(42L, result);
    }

    @Test
    public void primitiveReturnDouble() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("computeDouble", new Class<?>[]{double.class});
        final Object result = handler.invoke(target, null, new Object[]{2.5});
        assertEquals(5.0, (double) result, 0.001);
    }

    @Test
    public void primitiveReturnFloat() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("computeFloat", new Class<?>[]{float.class});
        final Object result = handler.invoke(target, null, new Object[]{2.0f});
        assertEquals(6.0f, (float) result, 0.001f);
    }

    @Test
    public void primitiveReturnByte() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("toByte", new Class<?>[]{int.class});
        final Object result = handler.invoke(target, null, new Object[]{42});
        assertEquals((byte) 42, result);
    }

    @Test
    public void primitiveReturnShort() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("toShort", new Class<?>[]{int.class});
        final Object result = handler.invoke(target, null, new Object[]{1000});
        assertEquals((short) 1000, result);
    }

    @Test
    public void primitiveReturnChar() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("toChar", new Class<?>[]{int.class});
        final Object result = handler.invoke(target, null, new Object[]{65});
        assertEquals('A', result);
    }

    @Test
    public void objectArrayReturn() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("toArray", new Class<?>[]{String.class, String.class});
        final Object result = handler.invoke(target, null, new Object[]{"a", "b"});
        assertArrayEquals(new Object[]{"a", "b"}, (Object[]) result);
    }

    @Test
    public void methodWithNoParameters() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("noArgs", new Class<?>[0]);
        final Object result = handler.invoke(target, null, new Object[0]);
        assertEquals("no args", result);
    }

    @Test
    public void methodWithManyParameters() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("manyArgs",
                new Class<?>[]{String.class, int.class, long.class, double.class, boolean.class});
        final Object result = handler.invoke(target, null, new Object[]{"x", 1, 2L, 3.0, true});
        assertEquals("x123.0true", result);
    }

    @Test
    public void throwsCheckedException() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("failChecked", new Class<?>[]{String.class});
        try {
            handler.invoke(target, null, new Object[]{"test"});
            fail("Expected IOException");
        } catch (final IOException e) {
            assertEquals("checked: test", e.getMessage());
        }
    }

    @Test
    public void throwsUncheckedException() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("failUnchecked", new Class<?>[]{String.class});
        try {
            handler.invoke(target, null, new Object[]{"test"});
            fail("Expected IllegalArgumentException");
        } catch (final IllegalArgumentException e) {
            assertEquals("unchecked: test", e.getMessage());
        }
    }

    @Test
    public void overloadedMethodsByParamTypes() throws Throwable {
        final ProceedHandler oneArg = new ProceedHandler("overloaded", new Class<?>[]{String.class});
        assertEquals("one:a", oneArg.invoke(target, null, new Object[]{"a"}));

        final ProceedHandler twoArgs = new ProceedHandler("overloaded", new Class<?>[]{String.class, String.class});
        assertEquals("two:x,y", twoArgs.invoke(target, null, new Object[]{"x", "y"}));
    }

    @Test
    public void methodCacheSecondCallUsesCached() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("greet", new Class<?>[]{String.class});

        assertEquals("Hello, Alice!", handler.invoke(target, null, new Object[]{"Alice"}));
        assertEquals("Hello, Bob!", handler.invoke(target, null, new Object[]{"Bob"}));
    }

    @Test
    public void missingRenamedMethodThrowsClearError() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("nonExistent", new Class<?>[0]);
        try {
            handler.invoke(target, null, new Object[0]);
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("jackknife$nonExistent"));
            assertTrue(e.getMessage().contains(SampleTarget.class.getName()));
        }
    }

    @Test
    public void staticMethodNullProxyWithoutClassNameThrows() throws Throwable {
        final ProceedHandler handler = new ProceedHandler("multiply", new Class<?>[]{long.class, long.class});
        try {
            handler.invoke(null, null, new Object[]{6L, 7L});
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("proxy is null"));
        }
    }
}
