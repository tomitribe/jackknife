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
 * Lightweight timing-only handler. Logs one JSON line per call
 * with class, method, time, and status. No args or return values.
 */
public final class TimingHandler implements InvocationHandler {

    private final InvocationHandler delegate;

    public TimingHandler(final InvocationHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final String className = method != null ? method.getDeclaringClass().getSimpleName() : "unknown";
        final String methodName = method != null ? method.getName().replace("jackknife$", "") : "unknown";

        final long start = System.nanoTime();
        String status = "returned";
        try {
            return delegate.invoke(proxy, method, args);
        } catch (final Throwable t) {
            status = "thrown";
            throw t;
        } finally {
            final long elapsed = System.nanoTime() - start;
            final String time = DebugHandler.formatNanos(elapsed);
            System.out.println("JACKKNIFE {\"event\":\"call\",\"time\":\"" + time
                    + "\",\"class\":\"" + className
                    + "\",\"method\":\"" + methodName
                    + "\",\"status\":\"" + status + "\"}");
        }
    }
}
