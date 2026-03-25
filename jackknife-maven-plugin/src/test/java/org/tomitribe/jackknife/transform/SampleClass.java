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
package org.tomitribe.jackknife.transform;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * Sample class used for testing bytecode transformation.
 * Contains a variety of method shapes to exercise HandlerEnhancer.
 */
@SuppressWarnings("unused")
public class SampleClass {

    // Instance method returning String
    public String greet(final String name) {
        return "Hello, " + name + "!";
    }

    // Instance method returning int (primitive)
    public int add(final int a, final int b) {
        return a + b;
    }

    // Instance void method
    public void doWork() {
        System.out.println("working");
    }

    // Static method returning long
    public static long multiply(final long x, final long y) {
        return x * y;
    }

    // Instance method returning boolean
    public boolean check(final String value, final int threshold) {
        return value.length() > threshold;
    }

    // ---- Additional method shapes ----

    // Primitive returns
    public byte returnByte(final int x) {
        return (byte) x;
    }

    public short returnShort(final int x) {
        return (short) x;
    }

    public char returnChar(final int x) {
        return (char) x;
    }

    public float returnFloat(final float x) {
        return x * 2.0f;
    }

    public double returnDouble(final double x) {
        return x * 2.0;
    }

    // Array returns
    public Object[] returnObjectArray(final String a, final String b) {
        return new Object[]{a, b};
    }

    public int[] returnIntArray() {
        return new int[]{1, 2, 3};
    }

    public byte[] returnByteArray() {
        return new byte[]{1, 2, 3};
    }

    // No parameters
    public String noParams() {
        return "none";
    }

    // Many parameters
    public String manyParams(final String a, final int b, final long c, final double d, final boolean e) {
        return a + b + c + d + e;
    }

    // Varargs
    public int sumVarargs(final int... values) {
        int sum = 0;
        for (final int v : values) {
            sum += v;
        }
        return sum;
    }

    // Generic parameters
    public String joinList(final List<String> items) {
        return String.join(",", items);
    }

    public String mapLookup(final Map<String, String> map, final String key) {
        return map.get(key);
    }

    // Generic return type
    public List<String> toList(final String a, final String b) {
        return List.of(a, b);
    }

    // Synchronized method
    public synchronized String syncMethod(final String input) {
        return "synced:" + input;
    }

    // Throws checked exception
    public String throwsChecked(final String input) throws IOException {
        throw new IOException("checked: " + input);
    }

    // Throws unchecked exception
    public String throwsUnchecked(final String input) {
        throw new IllegalArgumentException("unchecked: " + input);
    }

    // Overloaded methods
    public String overloaded(final String a) {
        return "one:" + a;
    }

    public String overloaded(final String a, final String b) {
        return "two:" + a + "," + b;
    }

    public String overloaded(final int a) {
        return "int:" + a;
    }

    // Private method
    private String privateMethod(final String input) {
        return "private:" + input;
    }

    // Protected method
    protected String protectedMethod(final String input) {
        return "protected:" + input;
    }

    // Package-private method
    String packageMethod(final String input) {
        return "package:" + input;
    }

    // Final method
    public final String finalMethod(final String input) {
        return "final:" + input;
    }

    // Static void
    public static void staticVoid() {
        // no-op
    }

    // Static with return
    public static String staticReturn(final String input) {
        return "static:" + input;
    }

    // ---- Annotated methods for annotation movement tests ----

    @Deprecated
    public String annotatedMethod(final String input) {
        return "annotated:" + input;
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public String multiAnnotated(final String input) {
        return "multi:" + input;
    }

    public String paramAnnotated(@TestAnnotation final String input, final int count) {
        return "param:" + input + count;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface TestAnnotation {
    }
}
