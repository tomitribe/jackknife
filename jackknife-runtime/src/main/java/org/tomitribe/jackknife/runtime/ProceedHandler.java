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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The final handler in the chain — calls the renamed original method
 * via reflection. Handles all method shapes (void, primitives, objects,
 * arrays, static, varargs) without any bytecode generation.
 *
 * Caches Method lookups for repeated calls.
 */
public final class ProceedHandler implements InvocationHandler {

    private static final String PREFIX = "jackknife$";
    private final ConcurrentMap<String, Method> methodCache = new ConcurrentHashMap<>();

    private final String targetClassName;
    private final String methodName;
    private final Class<?>[] parameterTypes;

    public ProceedHandler(final String methodName, final Class<?>[] parameterTypes) {
        this(null, methodName, parameterTypes);
    }

    public ProceedHandler(final String targetClassName, final String methodName, final Class<?>[] parameterTypes) {
        this.targetClassName = targetClassName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final Class<?> targetClass;
        if (proxy != null) {
            targetClass = proxy.getClass();
        } else if (targetClassName != null) {
            targetClass = Class.forName(targetClassName, true, Thread.currentThread().getContextClassLoader());
        } else {
            throw new IllegalStateException("Cannot determine target class: proxy is null and no targetClassName configured");
        }
        final String cacheKey = targetClass.getName() + "." + methodName;

        final Method renamed = methodCache.computeIfAbsent(cacheKey, k -> {
            try {
                final Method m = targetClass.getDeclaredMethod(PREFIX + methodName, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (final NoSuchMethodException e) {
                throw new IllegalStateException("Cannot find renamed method " + PREFIX + methodName
                        + " on " + targetClass.getName(), e);
            }
        });

        try {
            return renamed.invoke(proxy, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
