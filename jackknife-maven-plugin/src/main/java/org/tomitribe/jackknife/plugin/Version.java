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

import java.util.Objects;

/**
 * Comparable version that splits on non-numeric characters and compares
 * integer components. Correctly sorts 2.9.1 < 2.14.0 < 2.15.3.
 */
final class Version implements Comparable<Version> {

    private final String value;
    private final int[] components;

    Version(final String value) {
        this.value = value;
        this.components = parseComponents(value);
    }

    String get() {
        return value;
    }

    @Override
    public int compareTo(final Version that) {
        for (int i = 0; i < components.length; i++) {
            if (that.components.length <= i) {
                return 1;
            }

            final int compare = Integer.compare(this.components[i], that.components[i]);
            if (compare != 0) {
                return compare;
            }
        }

        return this.value.compareTo(that.value);
    }

    private static int[] parseComponents(final String version) {
        final String[] split = version.split("[.+_ a-zA-Z-]+");
        final int[] result = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                result[i] = Integer.parseInt(split[i]);
            } catch (final NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Version version = (Version) o;
        return Objects.equals(value, version.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
