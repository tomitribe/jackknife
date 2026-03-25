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
 * Normal tests that exercise App, dependency methods, and test helpers.
 * Knows nothing about jackknife. When Join.join is instrumented,
 * debug output appears during these test runs.
 */
public class AppTest {

    @Test
    public void processCallsJoinOnDep() {
        final App app = new App();
        final String result = app.process("a,b,c");
        assertEquals("a and b and c", result);
    }

    @Test
    public void formatCallsHelper() {
        final App app = new App();
        final String result = app.format("hello");
        assertEquals("[hello]", result);
    }

    @Test
    public void directDepCallFromTest() {
        final String result = Join.join(", ", List.of("x", "y", "z"));
        assertEquals("x, y, z", result);
    }

    @Test
    public void testHelperFromTest() {
        final String result = TestHelper.compute("hello");
        assertEquals("HELLO", result);
    }
}
