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

import java.lang.reflect.InvocationHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Global registry for InvocationHandlers keyed by class+method+descriptor.
 *
 * The transformed bytecode calls getHandler() to look up the handler chain
 * for each instrumented method. If no handler is registered, returns null
 * and the wrapper falls back to a direct call to the original method.
 */
public final class HandlerRegistry {

    private static final ConcurrentMap<String, InvocationHandler> HANDLERS = new ConcurrentHashMap<>();

    private HandlerRegistry() {
    }

    /**
     * Look up the handler for a specific method.
     * Called by generated wrapper bytecode.
     *
     * @param className the fully qualified class name (dotted)
     * @param methodName the method name
     * @param descriptor the method descriptor (ASM format)
     * @return the registered InvocationHandler, or null if none
     */
    public static InvocationHandler getHandler(final String className, final String methodName, final String descriptor) {
        final String key = key(className, methodName, descriptor);
        return HANDLERS.get(key);
    }

    /**
     * Register a handler for a specific method.
     */
    public static void register(final String className, final String methodName, final String descriptor,
                                final InvocationHandler handler) {
        final String key = key(className, methodName, descriptor);
        HANDLERS.put(key, handler);
    }

    /**
     * Remove a handler for a specific method.
     */
    public static void unregister(final String className, final String methodName, final String descriptor) {
        final String key = key(className, methodName, descriptor);
        HANDLERS.remove(key);
    }

    /**
     * Remove all registered handlers.
     */
    public static void clear() {
        HANDLERS.clear();
    }

    private static String key(final String className, final String methodName, final String descriptor) {
        return className + "." + methodName + descriptor;
    }
}
