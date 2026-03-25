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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InvocationHandler that logs method entry (args), exit (return value),
 * and thrown exceptions. Delegates to the next handler in the chain.
 *
 * Large values (over threshold or containing newlines) are written to
 * capture files and referenced in the console output.
 */
public final class DebugHandler implements InvocationHandler {

    private static final int DEFAULT_THRESHOLD = 500;
    private static final AtomicInteger CAPTURE_COUNTER = new AtomicInteger(0);

    private final InvocationHandler delegate;
    private final int threshold;
    private final File capturesDir;

    public DebugHandler(final InvocationHandler delegate) {
        this(delegate, DEFAULT_THRESHOLD, defaultCapturesDir());
    }

    public DebugHandler(final InvocationHandler delegate, final int threshold, final File capturesDir) {
        this.delegate = delegate;
        this.threshold = threshold;
        this.capturesDir = capturesDir;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final String label = proxy != null
                ? proxy.getClass().getName() + "." + (method != null ? method.getName() : "unknown")
                : (method != null ? method.getName() : "unknown");

        // Log entry
        final String argsStr = formatArgs(args);
        final String entryOutput = formatValue("ENTER " + label, argsStr);
        System.out.println(entryOutput);

        try {
            final Object result = delegate.invoke(proxy, method, args);

            // Log exit
            final String resultStr = formatObject(result);
            final String exitOutput = formatValue("EXIT  " + label + " ->", resultStr);
            System.out.println(exitOutput);

            return result;
        } catch (final Throwable t) {
            System.out.println("THROW " + label + " -> " + t.getClass().getName() + ": " + t.getMessage());
            throw t;
        }
    }

    private String formatArgs(final Object[] args) {
        if (args == null || args.length == 0) {
            return "()";
        }

        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatObject(args[i]));
        }
        sb.append(")");
        return sb.toString();
    }

    private String formatObject(final Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj.getClass().isArray()) {
            if (obj instanceof Object[]) {
                return Arrays.deepToString((Object[]) obj);
            }
            if (obj instanceof byte[]) {
                return "byte[" + ((byte[]) obj).length + "]";
            }
            if (obj instanceof int[]) {
                return Arrays.toString((int[]) obj);
            }
            if (obj instanceof long[]) {
                return Arrays.toString((long[]) obj);
            }
            if (obj instanceof double[]) {
                return Arrays.toString((double[]) obj);
            }
            if (obj instanceof boolean[]) {
                return Arrays.toString((boolean[]) obj);
            }
            if (obj instanceof char[]) {
                return Arrays.toString((char[]) obj);
            }
            if (obj instanceof float[]) {
                return Arrays.toString((float[]) obj);
            }
            if (obj instanceof short[]) {
                return Arrays.toString((short[]) obj);
            }
        }
        return obj.toString();
    }

    /**
     * Format a value for output. If the value is large or contains newlines,
     * write it to a capture file and return a reference.
     */
    private String formatValue(final String prefix, final String value) {
        final String fullLine = prefix + " " + value;

        if (fullLine.length() > threshold || value.contains("\n")) {
            return prefix + " " + writeCapture(value);
        }

        return fullLine;
    }

    private String writeCapture(final String content) {
        capturesDir.mkdirs();

        final int seq = CAPTURE_COUNTER.incrementAndGet();
        final String fileName = "capture-" + String.format("%04d", seq) + ".txt";
        final File file = new File(capturesDir, fileName);

        try (final PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.print(content);
        } catch (final IOException e) {
            return "[capture failed: " + e.getMessage() + "]";
        }

        return "[file: " + file.getPath() + "]";
    }

    private static File defaultCapturesDir() {
        return new File("target/jackknife/captures");
    }
}
