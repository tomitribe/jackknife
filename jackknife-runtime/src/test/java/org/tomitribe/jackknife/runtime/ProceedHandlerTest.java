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
import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProceedHandlerTest {

    private final ProceedHandler handler = new ProceedHandler();
    private final SampleTarget target = new SampleTarget();

    private Method method(final String name, final Class<?>... paramTypes) throws NoSuchMethodException {
        final Method m = SampleTarget.class.getDeclaredMethod("jackknife$" + name, paramTypes);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void instanceMethodWithReturnValue() throws Throwable {
        final Object result = handler.invoke(target, method("greet", String.class), new Object[]{"World"});
        assertEquals("Hello, World!", result);
    }

    @Test
    public void instanceVoidMethod() throws Throwable {
        final Object result = handler.invoke(target, method("doWork"), new Object[0]);
        assertNull(result);
    }

    @Test
    public void staticMethodWithReturnValue() throws Throwable {
        final Object result = handler.invoke(null, method("multiply", long.class, long.class), new Object[]{6L, 7L});
        assertEquals(42L, result);
    }

    @Test
    public void staticVoidMethod() throws Throwable {
        final Object result = handler.invoke(null, method("staticVoid"), new Object[0]);
        assertNull(result);
    }

    @Test
    public void primitiveReturnInt() throws Throwable {
        final Object result = handler.invoke(target, method("add", int.class, int.class), new Object[]{3, 4});
        assertEquals(7, result);
    }

    @Test
    public void primitiveReturnBoolean() throws Throwable {
        final Object result = handler.invoke(target, method("check", String.class, int.class), new Object[]{"hello", 3});
        assertEquals(true, result);
    }

    @Test
    public void primitiveReturnLong() throws Throwable {
        final Object result = handler.invoke(target, method("computeLong", long.class), new Object[]{21L});
        assertEquals(42L, result);
    }

    @Test
    public void primitiveReturnDouble() throws Throwable {
        final Object result = handler.invoke(target, method("computeDouble", double.class), new Object[]{2.5});
        assertEquals(5.0, (double) result, 0.001);
    }

    @Test
    public void primitiveReturnFloat() throws Throwable {
        final Object result = handler.invoke(target, method("computeFloat", float.class), new Object[]{2.0f});
        assertEquals(6.0f, (float) result, 0.001f);
    }

    @Test
    public void primitiveReturnByte() throws Throwable {
        final Object result = handler.invoke(target, method("toByte", int.class), new Object[]{42});
        assertEquals((byte) 42, result);
    }

    @Test
    public void primitiveReturnShort() throws Throwable {
        final Object result = handler.invoke(target, method("toShort", int.class), new Object[]{1000});
        assertEquals((short) 1000, result);
    }

    @Test
    public void primitiveReturnChar() throws Throwable {
        final Object result = handler.invoke(target, method("toChar", int.class), new Object[]{65});
        assertEquals('A', result);
    }

    @Test
    public void objectArrayReturn() throws Throwable {
        final Object result = handler.invoke(target, method("toArray", String.class, String.class), new Object[]{"a", "b"});
        assertArrayEquals(new Object[]{"a", "b"}, (Object[]) result);
    }

    @Test
    public void methodWithNoParameters() throws Throwable {
        final Object result = handler.invoke(target, method("noArgs"), new Object[0]);
        assertEquals("no args", result);
    }

    @Test
    public void methodWithManyParameters() throws Throwable {
        final Object result = handler.invoke(target,
                method("manyArgs", String.class, int.class, long.class, double.class, boolean.class),
                new Object[]{"x", 1, 2L, 3.0, true});
        assertEquals("x123.0true", result);
    }

    @Test
    public void throwsCheckedException() throws Throwable {
        try {
            handler.invoke(target, method("failChecked", String.class), new Object[]{"test"});
            fail("Expected IOException");
        } catch (final IOException e) {
            assertEquals("checked: test", e.getMessage());
        }
    }

    @Test
    public void throwsUncheckedException() throws Throwable {
        try {
            handler.invoke(target, method("failUnchecked", String.class), new Object[]{"test"});
            fail("Expected IllegalArgumentException");
        } catch (final IllegalArgumentException e) {
            assertEquals("unchecked: test", e.getMessage());
        }
    }

    @Test
    public void overloadedMethodsByParamTypes() throws Throwable {
        assertEquals("one:a", handler.invoke(target, method("overloaded", String.class), new Object[]{"a"}));
        assertEquals("two:x,y", handler.invoke(target, method("overloaded", String.class, String.class), new Object[]{"x", "y"}));
    }

    @Test
    public void methodCacheSecondCallUsesCached() throws Throwable {
        final Method m = method("greet", String.class);
        assertEquals("Hello, Alice!", handler.invoke(target, m, new Object[]{"Alice"}));
        assertEquals("Hello, Bob!", handler.invoke(target, m, new Object[]{"Bob"}));
    }
}
