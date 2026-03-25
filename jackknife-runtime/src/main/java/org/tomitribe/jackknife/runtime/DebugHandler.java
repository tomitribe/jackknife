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
 * InvocationHandler that produces one JSON line per call with args,
 * return value or exception, and timing. Large events are written
 * to capture files with a summary line on the console.
 *
 * Output format:
 *   JACKKNIFE {"event":"call","time":"836.0us","class":"Join","method":"join","args":[", ",["x","y","z"]],"return":"x, y, z"}
 *   JACKKNIFE {"event":"call","time":"2.3ms","class":"Join","method":"join","status":"returned","file":"capture-0012.txt"}
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
        final String className = method != null ? method.getDeclaringClass().getSimpleName() : "unknown";
        final String methodName = method != null ? method.getName().replace("jackknife$", "") : "unknown";

        final long start = System.nanoTime();
        try {
            final Object result = delegate.invoke(proxy, method, args);
            final long elapsed = System.nanoTime() - start;
            final String time = formatNanos(elapsed);

            final String fullJson = buildCallJson(time, className, methodName, args, result, null);
            emit(fullJson, time, className, methodName, "returned", null);

            return result;
        } catch (final Throwable t) {
            final long elapsed = System.nanoTime() - start;
            final String time = formatNanos(elapsed);

            final String fullJson = buildCallJson(time, className, methodName, args, null, t);
            emit(fullJson, time, className, methodName, "thrown", t);

            throw t;
        }
    }

    private void emit(final String fullJson, final String time, final String className,
                      final String methodName, final String status, final Throwable exception) {
        final String line = "JACKKNIFE " + fullJson;

        if (line.length() <= threshold && !line.contains("\n")) {
            System.out.println(line);
        } else {
            // Write full event to capture file, print summary to console
            final String filePath = writeCapture(fullJson);
            final StringBuilder summary = new StringBuilder();
            summary.append("JACKKNIFE {\"event\":\"call\"");
            summary.append(",\"time\":\"").append(time).append("\"");
            summary.append(",\"class\":\"").append(className).append("\"");
            summary.append(",\"method\":\"").append(methodName).append("\"");
            summary.append(",\"status\":\"").append(status).append("\"");
            if (exception != null) {
                summary.append(",\"exception\":\"").append(exception.getClass().getSimpleName()).append("\"");
            }
            summary.append(",\"file\":\"").append(filePath).append("\"");
            summary.append("}");
            System.out.println(summary);
        }
    }

    private String buildCallJson(final String time, final String className, final String methodName,
                                 final Object[] args, final Object result, final Throwable exception) {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"event\":\"call\"");
        sb.append(",\"time\":\"").append(time).append("\"");
        sb.append(",\"class\":\"").append(className).append("\"");
        sb.append(",\"method\":\"").append(methodName).append("\"");
        sb.append(",\"args\":").append(formatArgs(args));

        if (exception != null) {
            sb.append(",\"exception\":{\"type\":\"").append(exception.getClass().getName()).append("\"");
            sb.append(",\"message\":").append(jsonString(exception.getMessage())).append("}");
        } else {
            sb.append(",\"return\":").append(toJson(result));
        }

        sb.append("}");
        return sb.toString();
    }

    private String formatArgs(final Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(toJson(args[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJson(final Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return jsonString((String) obj);
        }
        if (obj instanceof Boolean || obj instanceof Number) {
            return obj.toString();
        }
        if (obj instanceof byte[]) {
            return jsonString("byte[" + ((byte[]) obj).length + "]");
        }
        if (obj instanceof Object[]) {
            final StringBuilder sb = new StringBuilder("[");
            final Object[] arr = (Object[]) obj;
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(arr[i]));
            }
            sb.append("]");
            return sb.toString();
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
            return jsonString(new String((char[]) obj));
        }
        if (obj instanceof float[]) {
            return Arrays.toString((float[]) obj);
        }
        if (obj instanceof short[]) {
            return Arrays.toString((short[]) obj);
        }
        // Fallback: toString() as a JSON string
        return jsonString(obj.toString());
    }

    private static String jsonString(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    static String methodLabel(final Method method) {
        if (method == null) {
            return "unknown";
        }
        final String name = method.getName().replace("jackknife$", "");
        return method.getDeclaringClass().getName() + "." + name;
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

        return file.getPath();
    }

    static String formatNanos(final long nanos) {
        if (nanos < 1_000) {
            return nanos + "ns";
        }
        if (nanos < 1_000_000) {
            return String.format("%.1fus", nanos / 1_000.0);
        }
        if (nanos < 1_000_000_000) {
            return String.format("%.1fms", nanos / 1_000_000.0);
        }
        return String.format("%.2fs", nanos / 1_000_000_000.0);
    }

    private static File defaultCapturesDir() {
        return new File("target/jackknife/captures");
    }
}
