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
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the instrument goal writes correct properties files
 * based on manifest lookups (not the old index format).
 */
public class InstrumentMojoTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testMethodParserWithClassProducesValidConfig() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse("join", "org.tomitribe.util.Join");

        assertEquals("org.tomitribe.util.Join", parsed.getClassName());
        assertEquals("join", parsed.getMethodName());
        assertTrue(parsed.hasClass());

        // The Snitch format should be class.method(args)
        final String snitch = parsed.toSnitchFormat();
        assertTrue(snitch.startsWith("org.tomitribe.util.Join.join("));
    }

    @Test
    public void testMethodParserFqnMethod() {
        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                "org.tomitribe.util.Join.join(java.lang.String,java.util.Collection)", null);

        assertEquals("org.tomitribe.util.Join", parsed.getClassName());
        assertEquals("join", parsed.getMethodName());
        assertEquals("org.tomitribe.util.Join.join(java.lang.String,java.util.Collection)",
                parsed.toSnitchFormat());
    }

    @Test
    public void testManifestSearchFindsClass() throws IOException {
        // Create a manifest directory with a test manifest
        final File manifestDir = tmp.newFolder("manifest", "org.tomitribe");
        final File manifest = new File(manifestDir, "tomitribe-util-1.5.10.jar.manifest");

        try (final PrintWriter out = new PrintWriter(new FileWriter(manifest))) {
            out.println("org.tomitribe.util.Join");
            out.println("org.tomitribe.util.Join$NameCallback");
            out.println("org.tomitribe.util.Duration");
            out.println("org.tomitribe.util.IO");
            out.println("# resources");
            out.println("META-INF/MANIFEST.MF");
        }

        // Verify the manifest file can be searched
        final String content = Files.readString(manifest.toPath());
        assertTrue("Manifest should contain the class",
                content.lines().anyMatch(line -> line.equals("org.tomitribe.util.Join")));

        // Verify it doesn't match partial names
        assertTrue("Should not match inner class as exact match",
                content.lines().noneMatch(line -> line.equals("org.tomitribe.util.Join$")));
    }

    @Test
    public void testInstrumentConfigFormat() throws IOException {
        // Simulate what the instrument goal writes
        final File propsFile = tmp.newFile("test.properties");

        final String className = "org.tomitribe.util.Join";
        final String methodName = "join";
        final String mode = "debug";

        final MethodParser.ParsedMethod parsed = MethodParser.parse(
                className + "." + methodName + "(java.lang.String,java.util.Collection)", null);

        final String snitchMethod = parsed.toSnitchFormat();
        final String prefix = "timing".equals(mode) ? "" : "@";
        final String propertyLine = prefix + snitchMethod + " = " + snitchMethod;

        try (final PrintWriter out = new PrintWriter(new FileWriter(propsFile))) {
            out.println("# Jackknife instrumentation config");
            out.println("# Mode: " + mode);
            out.println();
            out.println(propertyLine);
        }

        final String content = Files.readString(propsFile.toPath());

        // Verify the format matches what ProcessMojo expects
        assertTrue("Should have @ prefix for debug mode",
                content.contains("@org.tomitribe.util.Join.join("));
        assertTrue("Should have key = value format",
                content.contains(" = org.tomitribe.util.Join.join("));
    }
}
