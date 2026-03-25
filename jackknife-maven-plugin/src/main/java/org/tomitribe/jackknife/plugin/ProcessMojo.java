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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.tomitribe.jackknife.transform.HandlerEnhancer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Automatic lifecycle participant that:
 * 1. Processes pending instrumentation requests from .jackknife/instrument/
 * 2. Swaps modified jars into the classpath via Artifact.setFile()
 *
 * Bound to the initialize phase so it runs before compilation.
 */
@Mojo(name = "process", defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.TEST)
public class ProcessMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");

        if (!jackknife.exists()) {
            return;
        }

        final File instrumentDir = new File(jackknife, "instrument");
        final File modifiedDir = new File(jackknife, "modified");

        processInstrumentations(instrumentDir, modifiedDir);
        swapModifiedJars(modifiedDir);
    }

    /**
     * Process any pending instrumentation config files in .jackknife/instrument/.
     * Applies HandlerEnhancer bytecode transformation and writes patched jars
     * to .jackknife/modified/. Moves the properties file as a receipt.
     */
    private void processInstrumentations(final File instrumentDir, final File modifiedDir) {
        if (!instrumentDir.exists() || !instrumentDir.isDirectory()) {
            return;
        }

        final File[] groupDirs = instrumentDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return;
        }

        for (final File groupDir : groupDirs) {
            final File[] propsFiles = groupDir.listFiles((final File dir, final String name) -> name.endsWith(".properties"));
            if (propsFiles == null) {
                continue;
            }

            for (final File propsFile : propsFiles) {
                processOneArtifact(propsFile, groupDir.getName(), modifiedDir);
            }
        }
    }

    private void processOneArtifact(final File propsFile, final String groupId, final File modifiedDir) {
        final String artifactFileName = propsFile.getName().substring(
                0, propsFile.getName().length() - ".properties".length());

        final File jarFile = findJarForArtifact(groupId, artifactFileName);
        if (jarFile == null) {
            getLog().warn("Cannot find jar for " + groupId + ":" + artifactFileName + " — skipping");
            return;
        }

        final File modGroupDir = new File(modifiedDir, groupId);
        modGroupDir.mkdirs();
        final File patchedJar = new File(modGroupDir, artifactFileName);

        if (patchedJar.exists()) {
            getLog().debug("Patched jar already exists: " + patchedJar.getName());
        } else {
            // Parse the instrumentation config
            final InstrumentConfig config = parseConfig(propsFile);
            if (config.isEmpty()) {
                getLog().warn("No instrumentation entries in " + propsFile.getName());
                return;
            }

            try {
                getLog().info("Transforming " + artifactFileName);
                transformJar(jarFile, patchedJar, config);
                getLog().info("  Wrote patched jar: " + patchedJar.getPath());
            } catch (final IOException e) {
                getLog().error("Failed to transform jar: " + e.getMessage());
                return;
            }
        }

        // Move the properties file to modified/ as a receipt
        final File receipt = new File(modGroupDir, propsFile.getName());
        try {
            Files.move(propsFile.toPath(), receipt.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            getLog().warn("Failed to move properties file: " + e.getMessage());
        }
    }

    /**
     * Transform a jar: apply HandlerEnhancer to matching classes,
     * copy everything else, inject handler config.
     */
    private void transformJar(final File sourceJar, final File targetJar, final InstrumentConfig config)
            throws IOException {

        try (final JarFile jar = new JarFile(sourceJar);
             final JarOutputStream out = new JarOutputStream(new FileOutputStream(targetJar))) {

            final var entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    out.putNextEntry(new JarEntry(entry.getName()));
                    out.closeEntry();
                    continue;
                }

                try (final InputStream is = jar.getInputStream(entry)) {
                    final byte[] bytes = is.readAllBytes();

                    if (entry.getName().endsWith(".class")) {
                        final String className = entry.getName()
                                .replace(".class", "")
                                .replace('/', '.');

                        final Set<String> methods = config.getMethodsForClass(className);
                        if (methods != null && !methods.isEmpty()) {
                            getLog().info("  Enhancing " + className + " methods: " + methods);
                            final byte[] enhanced = HandlerEnhancer.enhance(bytes, methods);
                            out.putNextEntry(new JarEntry(entry.getName()));
                            out.write(enhanced);
                            out.closeEntry();
                            continue;
                        }
                    }

                    // Copy unchanged
                    out.putNextEntry(new JarEntry(entry.getName()));
                    out.write(bytes);
                    out.closeEntry();
                }
            }

            // Inject handler config for runtime auto-discovery
            final String handlerConfig = config.toHandlerConfig();
            out.putNextEntry(new JarEntry("META-INF/jackknife/handlers.properties"));
            out.write(handlerConfig.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    /**
     * Swap all modified jars into the Maven classpath via Artifact.setFile().
     */
    private void swapModifiedJars(final File modifiedDir) {
        if (!modifiedDir.exists() || !modifiedDir.isDirectory()) {
            return;
        }

        final File[] groupDirs = modifiedDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return;
        }

        int swapped = 0;
        final Set<Artifact> artifacts = project.getArtifacts();

        for (final File groupDir : groupDirs) {
            final File[] jarFiles = groupDir.listFiles((final File dir, final String name) -> name.endsWith(".jar"));
            if (jarFiles == null) {
                continue;
            }

            for (final File patchedJar : jarFiles) {
                final String groupId = groupDir.getName();

                for (final Artifact artifact : artifacts) {
                    if (!artifact.getGroupId().equals(groupId)) {
                        continue;
                    }

                    final File originalFile = artifact.getFile();
                    if (originalFile != null && originalFile.getName().equals(patchedJar.getName())) {
                        artifact.setFile(patchedJar);
                        getLog().info("Swapped " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                                + ":" + artifact.getVersion() + " → " + patchedJar.getPath());
                        swapped++;
                        break;
                    }
                }
            }
        }

        if (swapped > 0) {
            getLog().info("Swapped " + swapped + " modified jar(s) into classpath");
        }
    }

    private File findJarForArtifact(final String groupId, final String artifactFileName) {
        for (final Artifact artifact : project.getArtifacts()) {
            if (!artifact.getGroupId().equals(groupId)) {
                continue;
            }
            final File file = artifact.getFile();
            if (file != null && file.getName().equals(artifactFileName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * Parse an instrumentation properties file.
     *
     * Format (one per line):
     *   @com.example.Foo.process(java.lang.String,int) = com.example.Foo.process(java.lang.String,int)
     *   com.example.Bar.execute() = com.example.Bar.execute()
     *
     * @ prefix = debug mode, no prefix = timing mode
     */
    private InstrumentConfig parseConfig(final File propsFile) {
        final InstrumentConfig config = new InstrumentConfig();

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

                // Extract class name and method name from FQN method spec
                final int parenStart = methodSpec.indexOf('(');
                final String fullName = parenStart >= 0 ? methodSpec.substring(0, parenStart) : methodSpec;
                final int lastDot = fullName.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }

                final String className = fullName.substring(0, lastDot);
                final String methodName = fullName.substring(lastDot + 1);

                // Parse parameter types
                final String argsStr = parenStart >= 0 && methodSpec.contains(")")
                        ? methodSpec.substring(parenStart + 1, methodSpec.lastIndexOf(')'))
                        : "";

                config.add(className, methodName, mode, argsStr);
            }
        } catch (final IOException e) {
            // skip
        }

        return config;
    }

    /**
     * Holds parsed instrumentation configuration.
     */
    static final class InstrumentConfig {

        // className -> set of method names to wrap
        private final Map<String, Set<String>> methodsByClass = new LinkedHashMap<>();
        // list of handler entries for the runtime config file
        private final List<HandlerEntry> entries = new ArrayList<>();

        void add(final String className, final String methodName, final String mode,
                 final String paramTypes) {
            methodsByClass.computeIfAbsent(className, k -> new HashSet<>()).add(methodName);
            entries.add(new HandlerEntry(mode, className, methodName, paramTypes));
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        Set<String> getMethodsForClass(final String className) {
            return methodsByClass.get(className);
        }

        /**
         * Generate the META-INF/jackknife/handlers.properties content
         * for runtime auto-discovery.
         */
        String toHandlerConfig() {
            final StringBuilder sb = new StringBuilder();
            sb.append("# Jackknife handler configuration — auto-generated\n");
            sb.append("# Format: mode className methodName\n");
            for (final HandlerEntry entry : entries) {
                sb.append(entry.mode).append(" ")
                        .append(entry.className).append(" ")
                        .append(entry.methodName).append("\n");
            }
            return sb.toString();
        }

        record HandlerEntry(String mode, String className, String methodName, String paramTypes) {
        }
    }
}
