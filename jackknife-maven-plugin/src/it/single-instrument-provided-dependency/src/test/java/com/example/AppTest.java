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
package com.example;

import org.junit.Test;
import org.tomitribe.util.Join;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that exercise App and the provided-scope dependency directly.
 * Provided deps are on the compile and test classpath, so both App
 * and this test can use Join. When Join.join is instrumented,
 * debug JSON output appears during these tests.
 */
public class AppTest {

    @Test
    public void processCallsJoinOnDep() {
        final App app = new App();
        final String result = app.process("a,b,c");
        assertEquals("a and b and c", result);
    }

    @Test
    public void formatTrimsAndBrackets() {
        final App app = new App();
        final String result = app.format("  padded  ");
        assertEquals("[padded]", result);
    }

    @Test
    public void directDepCallFromTest() {
        final String result = Join.join(", ", List.of("x", "y", "z"));
        assertEquals("x, y, z", result);
    }

    @Test
    public void joinWithDifferentDelimiter() {
        final String result = Join.join(" and ", List.of("a", "b", "c"));
        assertEquals("a and b and c", result);
    }
}
