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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.tomitribe.jackknife.transform.HandlerEnhancer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enhances compiled project classes with InvocationHandler delegation.
 * Reads instrumentation configs from .jackknife/instrument/_project/
 * and applies HandlerEnhancer to matching .class files in target/classes/
 * and target/test-classes/.
 *
 * Bound to process-test-classes so both directories exist.
 * Writes META-INF/jackknife/handlers.properties to target/classes/
 * for runtime auto-discovery.
 */
@Mojo(name = "enhance", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES)
public class EnhanceClassesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
    private File testClassesDirectory;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File projectDir = new File(new File(jackknife, "instrument"), "_project");

        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return;
        }

        final File[] propsFiles = projectDir.listFiles((final File dir, final String name) -> name.endsWith(".properties"));
        if (propsFiles == null || propsFiles.length == 0) {
            return;
        }

        // Parse all project instrument configs
        final Map<String, Set<String>> methodsByClass = new LinkedHashMap<>();
        final java.util.List<HandlerEntry> entries = new java.util.ArrayList<>();

        for (final File propsFile : propsFiles) {
            parseConfig(propsFile, methodsByClass, entries);
        }

        if (methodsByClass.isEmpty()) {
            return;
        }

        int enhanced = 0;
        enhanced += enhanceDirectory(classesDirectory, methodsByClass);
        enhanced += enhanceDirectory(testClassesDirectory, methodsByClass);

        if (enhanced > 0) {
            // Write handlers.properties to target/classes for runtime auto-discovery
            writeHandlerConfig(classesDirectory, entries);
            getLog().info("Enhanced " + enhanced + " class(es) in project code");
        }
    }

    private int enhanceDirectory(final File dir, final Map<String, Set<String>> methodsByClass) {
        if (dir == null || !dir.exists()) {
            return 0;
        }

        int count = 0;
        for (final Map.Entry<String, Set<String>> entry : methodsByClass.entrySet()) {
            final String className = entry.getKey();
            final Set<String> methods = entry.getValue();

            final String classPath = className.replace('.', '/') + ".class";
            final File classFile = new File(dir, classPath);

            if (!classFile.exists()) {
                continue;
            }

            try {
                final byte[] original = Files.readAllBytes(classFile.toPath());
                final byte[] transformed = HandlerEnhancer.enhance(original, methods);
                Files.write(classFile.toPath(), transformed);
                getLog().info("  Enhanced " + className + " methods: " + methods);
                count++;
            } catch (final IOException e) {
                getLog().warn("Failed to enhance " + className + ": " + e.getMessage());
            }
        }
        return count;
    }

    private void parseConfig(final File propsFile, final Map<String, Set<String>> methodsByClass,
                             final java.util.List<HandlerEntry> entries) {
        try (final BufferedReader reader = new BufferedReader(new FileReader(propsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse: [@]class.method(args) = class.method(args)
                final String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line;

                final boolean isDebug = key.startsWith("@");
                final String methodSpec = isDebug ? key.substring(1) : key;
                final String mode = isDebug ? "debug" : "timing";

                final int parenStart = methodSpec.indexOf('(');
                final String fullName = parenStart >= 0 ? methodSpec.substring(0, parenStart) : methodSpec;
                final int lastDot = fullName.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }

                final String className = fullName.substring(0, lastDot);
                final String methodName = fullName.substring(lastDot + 1);

                methodsByClass.computeIfAbsent(className, k -> new HashSet<>()).add(methodName);
                entries.add(new HandlerEntry(mode, className, methodName));
            }
        } catch (final IOException e) {
            // skip
        }
    }

    private void writeHandlerConfig(final File dir, final java.util.List<HandlerEntry> entries) {
        if (dir == null || !dir.exists()) {
            return;
        }

        final File configDir = new File(dir, "META-INF/jackknife");
        configDir.mkdirs();
        final File configFile = new File(configDir, "handlers.properties");

        // Append to existing config (ProcessMojo may have written entries for dependency jars)
        try (final PrintWriter out = new PrintWriter(new FileWriter(configFile, true))) {
            out.println("# Jackknife project class handlers — auto-generated");
            for (final HandlerEntry entry : entries) {
                out.println(entry.mode + " " + entry.className + " " + entry.methodName);
            }
        } catch (final IOException e) {
            getLog().warn("Failed to write handler config: " + e.getMessage());
        }
    }

    private record HandlerEntry(String mode, String className, String methodName) {
    }
}
