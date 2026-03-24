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
package org.tomitribe.jackknife.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MethodParserTest {

    @Test
    public void testSnitchFormat() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "com.example.Foo.process(java.lang.String,int)", null);

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasArgs());
        assertEquals(2, parsed.getArgTypes().size());
        assertEquals("java.lang.String", parsed.getArgTypes().get(0));
        assertEquals("int", parsed.getArgTypes().get(1));
        assertEquals("com.example.Foo.process(java.lang.String,int)", parsed.toSnitchFormat());
    }

    @Test
    public void testIndexFormatWithParamNames() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "public method process(java.lang.String input, int count) : boolean",
                "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasArgs());
        assertEquals(2, parsed.getArgTypes().size());
        assertEquals("java.lang.String", parsed.getArgTypes().get(0));
        assertEquals("int", parsed.getArgTypes().get(1));
    }

    @Test
    public void testMethodNameOnly() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process", null);

        assertNotNull(parsed);
        assertNull(parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertFalse(parsed.hasClass());
        assertFalse(parsed.hasArgs());
    }

    @Test
    public void testMethodNameWithClass() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process", "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasClass());
    }

    @Test
    public void testMethodNameWithArgs() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(int, String)", null);

        assertNotNull(parsed);
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasArgs());
        assertEquals(2, parsed.getArgTypes().size());
        assertEquals("int", parsed.getArgTypes().get(0));
        assertEquals("java.lang.String", parsed.getArgTypes().get(1));
    }

    @Test
    public void testEmptyArgs() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process()", null);

        assertNotNull(parsed);
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasArgs());
        assertEquals(0, parsed.getArgTypes().size());
    }

    @Test
    public void testStaticModifier() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "public static method getInstance() : com.example.Foo",
                "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("getInstance", parsed.getMethodName());
        assertTrue(parsed.hasArgs());
        assertEquals(0, parsed.getArgTypes().size());
    }

    @Test
    public void testFqnWithClassInMethod() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "com.example.Foo.process(java.lang.String name, int count)", null);

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertEquals(2, parsed.getArgTypes().size());
    }
}
