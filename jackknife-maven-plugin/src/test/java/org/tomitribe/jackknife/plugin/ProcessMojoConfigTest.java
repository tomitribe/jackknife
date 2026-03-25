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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for ProcessMojo.InstrumentConfig — the parsed instrumentation configuration.
 */
public class ProcessMojoConfigTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    // ---- InstrumentConfig direct tests ----

    @Test
    public void addCreatesMethodSetForClass() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "java.lang.String,int");

        final Set<String> methods = config.getMethodsForClass("com.example.Foo");
        assertNotNull(methods);
        assertTrue(methods.contains("process"));
    }

    @Test
    public void emptyConfigIsEmpty() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        assertTrue(config.isEmpty());
    }

    @Test
    public void nonEmptyConfigIsNotEmpty() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "");
        assertFalse(config.isEmpty());
    }

    @Test
    public void getMethodsForClassReturnsNullForUnknown() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "");
        assertNull(config.getMethodsForClass("com.example.Bar"));
    }

    @Test
    public void multipleMethodsSameClass() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "java.lang.String");
        config.add("com.example.Foo", "execute", "timing", "int");

        final Set<String> methods = config.getMethodsForClass("com.example.Foo");
        assertEquals(2, methods.size());
        assertTrue(methods.contains("process"));
        assertTrue(methods.contains("execute"));
    }

    @Test
    public void toHandlerConfigDebugMode() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "java.lang.String,int");

        final String output = config.toHandlerConfig();
        assertTrue("Should contain mode", output.contains("debug"));
        assertTrue("Should contain class", output.contains("com.example.Foo"));
        assertTrue("Should contain method", output.contains("process"));
        assertTrue("Should contain param types", output.contains("java.lang.String,int"));
    }

    @Test
    public void toHandlerConfigTimingMode() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Bar", "execute", "timing", "");

        final String output = config.toHandlerConfig();
        assertTrue("Should contain timing mode", output.contains("timing"));
        assertTrue("Should contain class", output.contains("com.example.Bar"));
        assertTrue("Should contain method", output.contains("execute"));
    }

    @Test
    public void toHandlerConfigNoParamTypes() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "noArgs", "debug", "");

        final String output = config.toHandlerConfig();
        // With empty param types, line should be "debug com.example.Foo noArgs\n"
        assertTrue(output.contains("debug com.example.Foo noArgs"));
    }

    @Test
    public void toHandlerConfigMultipleEntries() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "java.lang.String");
        config.add("com.example.Bar", "execute", "timing", "int");

        final String output = config.toHandlerConfig();
        assertTrue(output.contains("debug com.example.Foo process java.lang.String"));
        assertTrue(output.contains("timing com.example.Bar execute int"));
    }

    @Test
    public void toHandlerConfigHasHeader() {
        final ProcessMojo.InstrumentConfig config = new ProcessMojo.InstrumentConfig();
        config.add("com.example.Foo", "process", "debug", "");

        final String output = config.toHandlerConfig();
        assertTrue("Should have comment header", output.startsWith("#"));
    }
}
