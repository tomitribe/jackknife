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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Automatic lifecycle participant that:
 * 1. Processes pending instrumentation requests from .jackknife/instrument/
 * 2. Swaps modified jars into the classpath via Artifact.setFile()
 *
 * Bound to the initialize phase so it runs before compilation.
 */
@Mojo(name = "process", defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
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
            return; // No .jackknife directory — nothing to do
        }

        final File instrumentDir = new File(jackknife, "instrument");
        final File modifiedDir = new File(jackknife, "modified");

        // Step 1: Process pending instrumentation requests
        processInstrumentations(instrumentDir, modifiedDir);

        // Step 2: Swap modified jars into the classpath
        swapModifiedJars(modifiedDir);
    }

    /**
     * Process any pending instrumentation config files in .jackknife/instrument/.
     * Applies Snitch bytecode transformation and writes patched jars to .jackknife/modified/.
     * Moves the properties file from instrument/ to modified/ as a receipt.
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
                final String groupId = groupDir.getName();
                // Derive artifact filename: remove .properties suffix
                final String artifactFileName = propsFile.getName().substring(
                        0, propsFile.getName().length() - ".properties".length());

                // Find the actual jar in the resolved dependencies
                final File jarFile = findJarForArtifact(groupId, artifactFileName);
                if (jarFile == null) {
                    getLog().warn("Cannot find jar for " + groupId + ":" + artifactFileName + " — skipping instrumentation");
                    continue;
                }

                // Target location for the patched jar
                final File modGroupDir = new File(modifiedDir, groupId);
                modGroupDir.mkdirs();
                final File patchedJar = new File(modGroupDir, artifactFileName);

                if (patchedJar.exists()) {
                    getLog().debug("Patched jar already exists: " + patchedJar.getName());
                } else {
                    // TODO: Apply Snitch + Archie bytecode transformation
                    // For now, copy the original jar as a placeholder
                    // The actual instrumentation will be added when Snitch is enhanced
                    // with InvocationHandler delegation
                    try {
                        getLog().info("Processing instrumentation for " + artifactFileName);
                        Files.copy(jarFile.toPath(), patchedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLog().warn("  (bytecode transformation not yet implemented — using original jar)");
                    } catch (final IOException e) {
                        getLog().error("Failed to create patched jar: " + e.getMessage());
                        continue;
                    }
                }

                // Move the properties file to modified/ as a receipt
                final File receipt = new File(modGroupDir, propsFile.getName());
                try {
                    Files.move(propsFile.toPath(), receipt.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLog().info("Moved " + propsFile.getName() + " to modified/");
                } catch (final IOException e) {
                    getLog().warn("Failed to move properties file: " + e.getMessage());
                }
            }
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

                // Find the matching artifact and swap its file
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

    /**
     * Find the jar file for a given groupId and artifact filename in the resolved dependencies.
     */
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
}
