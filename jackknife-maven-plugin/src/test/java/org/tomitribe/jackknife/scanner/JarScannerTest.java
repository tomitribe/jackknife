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
package org.tomitribe.jackknife.scanner;

import org.junit.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JarScannerTest {

    /**
     * Test exception detection by scanning a class we know throws exceptions.
     * ClassReader itself throws IllegalArgumentException in its constructor.
     */
    @Test
    public void testThrownExceptionDetection() throws IOException {
        final StructureVisitor visitor = new StructureVisitor();
        try (final InputStream is = ClassReader.class.getResourceAsStream(
                "/" + ClassReader.class.getName().replace('.', '/') + ".class")) {
            assertNotNull("Should find ClassReader on classpath", is);
            final ClassReader reader = new ClassReader(is);
            reader.accept(visitor, 0);
        }

        final ClassStructure structure = visitor.build();
        assertNotNull(structure);

        // Look for any method that has thrown exceptions
        boolean foundThrownException = false;
        for (final ClassStructure.MethodInfo method : structure.getMethods()) {
            if (!method.getThrownExceptions().isEmpty()) {
                foundThrownException = true;
                System.out.println("Method " + method.getName() + " throws: " + method.getThrownExceptions());
            }
        }

        // Also print declared exceptions for comparison
        for (final ClassStructure.MethodInfo method : structure.getMethods()) {
            if (!method.getDeclaredExceptions().isEmpty()) {
                System.out.println("Method " + method.getName() + " declares: " + method.getDeclaredExceptions());
            }
        }

        // ClassReader should have some thrown exceptions (IllegalArgumentException at minimum)
        assertTrue("Should detect at least one thrown exception in ClassReader", foundThrownException);
    }
}
