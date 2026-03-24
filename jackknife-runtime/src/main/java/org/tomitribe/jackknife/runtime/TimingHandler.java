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
import java.lang.reflect.Method;

/**
 * InvocationHandler that measures elapsed time for method execution.
 * Delegates to the next handler in the chain.
 *
 * Lightweight — no value capture, no boxing overhead beyond what
 * the InvocationHandler pattern already introduces.
 */
public final class TimingHandler implements InvocationHandler {

    private final InvocationHandler delegate;

    public TimingHandler(final InvocationHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final String label = proxy != null ? proxy.getClass().getName() + "." : "";
        final String methodName = label + (method != null ? method.getName() : "unknown");

        final long start = System.nanoTime();
        try {
            return delegate.invoke(proxy, method, args);
        } finally {
            final long elapsed = System.nanoTime() - start;
            System.out.println("TIMING " + methodName + " " + formatNanos(elapsed));
        }
    }

    private static String formatNanos(final long nanos) {
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
}
