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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global registry for InvocationHandlers keyed by class+method+descriptor.
 *
 * The transformed bytecode calls getHandler() to look up the handler chain
 * for each instrumented method. On first access, auto-discovers handler
 * configs from META-INF/jackknife/handlers.properties on the classpath.
 *
 * If no handler is registered, returns null and the wrapper falls back
 * to a direct call to the original method.
 */
public final class HandlerRegistry {

    private static final ConcurrentMap<String, InvocationHandler> HANDLERS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final String CONFIG_PATH = "META-INF/jackknife/handlers.properties";

    private HandlerRegistry() {
    }

    /**
     * Look up the handler for a specific method.
     * Called by generated wrapper bytecode.
     * Auto-initializes from classpath config on first call.
     */
    public static InvocationHandler getHandler(final String className, final String methodName) {
        if (INITIALIZED.compareAndSet(false, true)) {
            autoDiscover();
        }
        final String key = key(className, methodName);
        return HANDLERS.get(key);
    }

    /**
     * Register a handler for a specific method.
     */
    public static void register(final String className, final String methodName,
                                final InvocationHandler handler) {
        final String key = key(className, methodName);
        HANDLERS.put(key, handler);
    }

    /**
     * Remove a handler for a specific method.
     */
    public static void unregister(final String className, final String methodName) {
        final String key = key(className, methodName);
        HANDLERS.remove(key);
    }

    /**
     * Remove all registered handlers and reset initialization state.
     */
    public static void clear() {
        HANDLERS.clear();
        INITIALIZED.set(false);
    }

    /**
     * Scan the classpath for META-INF/jackknife/handlers.properties files
     * and register handlers for each entry.
     *
     * File format (one per line):
     *   mode className methodName descriptor paramTypes
     *
     * Example:
     *   debug com.example.Foo process (Ljava/lang/String;I)Z java.lang.String,int
     *   timing com.example.Bar execute ()V
     */
    private static void autoDiscover() {
        try {
            final Enumeration<URL> configs = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(CONFIG_PATH);

            while (configs.hasMoreElements()) {
                final URL url = configs.nextElement();
                loadConfig(url);
            }
        } catch (final IOException e) {
            System.err.println("JACKKNIFE: Failed to discover handler configs: " + e.getMessage());
        }
    }

    private static void loadConfig(final URL url) {
        try (final InputStream is = url.openStream();
             final BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Format: mode className methodName
                final String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) {
                    continue;
                }

                final String mode = parts[0];
                final String className = parts[1];
                final String methodName = parts[2];

                final ProceedHandler proceed = new ProceedHandler();
                final InvocationHandler handler = buildChain(mode, proceed);

                register(className, methodName, handler);

                System.out.println("JACKKNIFE: Registered " + mode + " handler for "
                        + className + "." + methodName);
            }
        } catch (final IOException e) {
            System.err.println("JACKKNIFE: Failed to load config from " + url + ": " + e.getMessage());
        }
    }

    /**
     * Build the handler chain for a given mode.
     */
    private static InvocationHandler buildChain(final String mode, final ProceedHandler proceed) {
        return switch (mode) {
            case "debug" -> new DebugHandler(proceed);
            case "timing" -> new TimingHandler(proceed);
            case "all" -> new TimingHandler(new DebugHandler(proceed));
            default -> proceed;
        };
    }

    private static String key(final String className, final String methodName) {
        return className + "." + methodName;
    }
}
