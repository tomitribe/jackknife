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

import java.io.IOException;

/**
 * Test fixture that simulates a class after HandlerEnhancer transformation.
 * Methods prefixed with jackknife$ represent the renamed originals.
 */
@SuppressWarnings("unused")
public class SampleTarget {

    // Instance method returning String
    public String jackknife$greet(final String name) {
        return "Hello, " + name + "!";
    }

    // Instance method returning int (primitive)
    public int jackknife$add(final int a, final int b) {
        return a + b;
    }

    // Instance void method
    public void jackknife$doWork() {
        // no-op
    }

    // Instance method returning boolean
    public boolean jackknife$check(final String value, final int threshold) {
        return value.length() > threshold;
    }

    // Instance method returning long
    public long jackknife$computeLong(final long x) {
        return x * 2;
    }

    // Instance method returning double
    public double jackknife$computeDouble(final double x) {
        return x * 2.0;
    }

    // Instance method returning float
    public float jackknife$computeFloat(final float x) {
        return x * 3.0f;
    }

    // Instance method returning byte
    public byte jackknife$toByte(final int x) {
        return (byte) x;
    }

    // Instance method returning short
    public short jackknife$toShort(final int x) {
        return (short) x;
    }

    // Instance method returning char
    public char jackknife$toChar(final int x) {
        return (char) x;
    }

    // Instance method returning Object array
    public Object[] jackknife$toArray(final String a, final String b) {
        return new Object[]{a, b};
    }

    // Instance method with no parameters
    public String jackknife$noArgs() {
        return "no args";
    }

    // Instance method with many parameters
    public String jackknife$manyArgs(final String a, final int b, final long c,
                                     final double d, final boolean e) {
        return a + b + c + d + e;
    }

    // Static method returning long
    public static long jackknife$multiply(final long x, final long y) {
        return x * y;
    }

    // Static void method
    public static void jackknife$staticVoid() {
        // no-op
    }

    // Method that throws checked exception
    public String jackknife$failChecked(final String input) throws IOException {
        throw new IOException("checked: " + input);
    }

    // Method that throws unchecked exception
    public String jackknife$failUnchecked(final String input) {
        throw new IllegalArgumentException("unchecked: " + input);
    }

    // Overloaded methods (same name, different args)
    public String jackknife$overloaded(final String a) {
        return "one:" + a;
    }

    public String jackknife$overloaded(final String a, final String b) {
        return "two:" + a + "," + b;
    }
}
