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

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Test class that can import and use Join because
 * tomitribe-util is available at test scope.
 */
public class AppTest {

    @Test
    public void greetReturnsHello() {
        final App app = new App();
        final String result = app.greet("World");
        assertEquals("Hello, World", result);
    }

    @Test
    public void joinAvailableAtTestScope() {
        final String result = Join.join(" and ", Arrays.asList("a", "b", "c"));
        assertEquals("a and b and c", result);
    }
}
