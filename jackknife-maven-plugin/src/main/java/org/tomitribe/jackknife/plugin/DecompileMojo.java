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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.jar.Manifest;

/**
 * Decompiles a specific class from the project's dependencies using Vineflower.
 * Prints to stdout and caches to .jackknife/source/.
 *
 * Usage: mvn jackknife:decompile -Dclass=com.example.MyClass
 *        mvn jackknife:decompile -Dclass=com.example.MyClass -Dforce=true
 */
@Mojo(name = "decompile", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class DecompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "class", required = true)
    private String className;

    @Parameter(property = "force", defaultValue = "false")
    private boolean force;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String classPath = className.replace('.', '/');
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File sourceDir = new File(jackknife, "source");

        // Check cache first (unless force=true)
        final File cachedFile = new File(sourceDir, className.replace('.', '/') + ".java");
        if (!force && cachedFile.exists()) {
            getLog().info("Using cached source: " + cachedFile.getPath());
            printFile(cachedFile);
            return;
        }

        // Find which jar contains this class
        File targetJar = null;
        String artifactLabel = null;
        final Set<Artifact> artifacts = project.getArtifacts();
        for (final Artifact artifact : artifacts) {
            final File file = artifact.getFile();
            if (file == null || !file.exists() || !file.getName().endsWith(".jar")) {
                continue;
            }

            try (final java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
                if (jar.getEntry(classPath + ".class") != null) {
                    targetJar = file;
                    artifactLabel = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
                    getLog().info("Found " + className + " in " + artifactLabel);
                    break;
                }
            } catch (final IOException e) {
                // Skip unreadable jars
            }
        }

        if (targetJar == null) {
            throw new MojoFailureException("Class not found in any dependency: " + className);
        }

        // Decompile with Vineflower
        // Use exact class path + ".java" for matching, not prefix
        cachedFile.getParentFile().mkdirs();
        final String expectedEntryName = classPath + ".java";
        final CaptureResultSaver saver = new CaptureResultSaver(cachedFile, expectedEntryName);

        final Decompiler decompiler = Decompiler.builder()
                .inputs(targetJar)
                // Use exact class path for prefix (Vineflower will also decompile
                // inner classes like CustomField$Inner, but our saver only captures
                // the exact match)
                .allowedPrefixes(classPath)
                .output(saver)
                .option(IFernflowerPreferences.LOG_LEVEL, "error")
                .option(IFernflowerPreferences.THREADS, "1")
                .option(IFernflowerPreferences.REMOVE_BRIDGE, true)
                .option(IFernflowerPreferences.REMOVE_SYNTHETIC, true)
                .option(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, true)
                .option(IFernflowerPreferences.DECOMPILE_ENUM, true)
                .option(IFernflowerPreferences.DECOMPILE_ASSERTIONS, true)
                .option(IFernflowerPreferences.OVERRIDE_ANNOTATION, true)
                .option(IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS, true)
                .option(IFernflowerPreferences.PATTERN_MATCHING, true)
                .option(IFernflowerPreferences.SWITCH_EXPRESSIONS, true)
                .build();

        decompiler.decompile();

        if (!saver.hasCaptured()) {
            throw new MojoFailureException("Vineflower did not produce output for: " + className);
        }

        // Print the cached file to stdout
        printFile(cachedFile);
    }

    private void printFile(final File file) throws MojoExecutionException {
        try {
            final String content = java.nio.file.Files.readString(file.toPath());
            System.out.println(content);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to read " + file, e);
        }
    }

    /**
     * IResultSaver that captures decompiled output for a specific class,
     * writes to a cache file, and ignores everything else.
     *
     * Uses exact entry name matching to avoid capturing similarly-named
     * classes (e.g., CustomFieldDisplayType when CustomField was requested).
     */
    private static class CaptureResultSaver implements IResultSaver {

        private final File outputFile;
        private final String expectedEntryName;
        private boolean captured;

        CaptureResultSaver(final File outputFile, final String expectedEntryName) {
            this.outputFile = outputFile;
            this.expectedEntryName = expectedEntryName;
        }

        boolean hasCaptured() {
            return captured;
        }

        @Override
        public void saveClassEntry(final String path, final String archiveName,
                                   final String qualifiedName, final String entryName,
                                   final String content) {
            if (captured) {
                return;
            }
            if (content != null && entryName != null && entryName.equals(expectedEntryName)) {
                writeContent(content);
            }
        }

        @Override
        public void saveClassFile(final String path, final String qualifiedName,
                                  final String entryName, final String content, final int[] mapping) {
            if (captured) {
                return;
            }
            if (content != null && entryName != null && entryName.equals(expectedEntryName)) {
                writeContent(content);
            }
        }

        private void writeContent(final String content) {
            try (final PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
                out.print(content);
                captured = true;
            } catch (final IOException e) {
                throw new RuntimeException("Failed to write decompiled source to " + outputFile, e);
            }
        }

        @Override
        public void saveFolder(final String path) {
        }

        @Override
        public void copyFile(final String source, final String path, final String entryName) {
        }

        @Override
        public void createArchive(final String path, final String archiveName, final Manifest manifest) {
        }

        @Override
        public void saveDirEntry(final String path, final String archiveName, final String entryName) {
        }

        @Override
        public void copyEntry(final String source, final String path, final String archiveName, final String entry) {
        }

        @Override
        public void closeArchive(final String path, final String archiveName) {
        }
    }
}
