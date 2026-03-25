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

import java.util.List;

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

    // ---- Annotation prefix stripping ----

    @Test
    public void annotationPrefixStripped() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "@jakarta.inject.Inject public method process(java.lang.String input)",
                "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
    }

    // ---- Multiple modifiers ----

    @Test
    public void multipleModifiersStripped() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "public static final method process(int x) : void",
                "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("process", parsed.getMethodName());
    }

    // ---- Return type stripping ----

    @Test
    public void returnTypeStripped() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "process(int x) : boolean", "com.example.Foo");

        assertNotNull(parsed);
        assertEquals("process", parsed.getMethodName());
        assertEquals(List.of("int"), parsed.getArgTypes());
    }

    // ---- Simple type expansion ----

    @Test
    public void simpleTypeExpansionString() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(String)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.lang.String"), parsed.getArgTypes());
    }

    @Test
    public void simpleTypeExpansionList() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(List)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.util.List"), parsed.getArgTypes());
    }

    @Test
    public void simpleTypeExpansionMap() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(Map)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.util.Map"), parsed.getArgTypes());
    }

    @Test
    public void simpleTypeExpansionSet() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(Set)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.util.Set"), parsed.getArgTypes());
    }

    @Test
    public void simpleTypeExpansionCollection() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(Collection)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.util.Collection"), parsed.getArgTypes());
    }

    @Test
    public void primitivesUnchanged() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "process(int,long,boolean,double,float,byte,short,char)", null);
        assertNotNull(parsed);
        assertEquals(List.of("int", "long", "boolean", "double", "float", "byte", "short", "char"),
                parsed.getArgTypes());
    }

    @Test
    public void fullyQualifiedTypesUnchanged() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "process(java.lang.String,java.util.List)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.lang.String", "java.util.List"), parsed.getArgTypes());
    }

    @Test
    public void wrapperTypeExpansion() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "process(Integer,Long,Boolean,Double,Float,Byte,Short,Character)", null);
        assertNotNull(parsed);
        assertEquals(List.of(
                "java.lang.Integer", "java.lang.Long", "java.lang.Boolean",
                "java.lang.Double", "java.lang.Float", "java.lang.Byte",
                "java.lang.Short", "java.lang.Character"
        ), parsed.getArgTypes());
    }

    @Test
    public void objectAndClassExpansion() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(Object, Class)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.lang.Object", "java.lang.Class"), parsed.getArgTypes());
    }

    // ---- FQN class detection ----

    @Test
    public void fqnClassDetectedNoParens() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("com.example.Foo.process", null);

        assertNotNull(parsed);
        assertEquals("com.example.Foo", parsed.getClassName());
        assertEquals("process", parsed.getMethodName());
        assertTrue(parsed.hasClass());
    }

    @Test
    public void noClassReturnsNullClassName() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process", null);
        assertNotNull(parsed);
        assertNull(parsed.getClassName());
        assertFalse(parsed.hasClass());
    }

    // ---- toSnitchFormat ----

    @Test
    public void toSnitchFormatWithClass() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "com.example.Foo.process(java.lang.String,int)", null);
        assertEquals("com.example.Foo.process(java.lang.String,int)", parsed.toSnitchFormat());
    }

    @Test
    public void toSnitchFormatWithoutClass() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("process(int)", null);
        assertEquals("process(int)", parsed.toSnitchFormat());
    }

    @Test
    public void toStringMatchesSnitchFormat() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "com.example.Foo.process(java.lang.String)", null);
        assertNotNull(parsed);
        assertEquals(parsed.toSnitchFormat(), parsed.toString());
    }

    // ---- Edge cases ----

    @Test
    public void nullInput() {
        assertNull(MethodParser.parse(null, null));
    }

    @Test
    public void blankInput() {
        assertNull(MethodParser.parse("   ", null));
    }

    @Test
    public void parameterNamesStripped() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "process(java.lang.String name, int count, boolean flag)", null);
        assertNotNull(parsed);
        assertEquals(List.of("java.lang.String", "int", "boolean"), parsed.getArgTypes());
    }
}
