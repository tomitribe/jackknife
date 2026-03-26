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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

/**
 * Decompiles a class from project dependencies using Vineflower.
 *
 * On first request for any class in a jar, decompiles the ENTIRE jar
 * and writes one .java file per class to .jackknife/source/. All
 * subsequent lookups for classes in that jar are direct file reads
 * with no Maven invocation needed.
 *
 * Usage: mvn jackknife:decompile -Dclass=com.example.MyClass
 */
@Mojo(name = "decompile", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class DecompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "class", required = true)
    private String className;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String classPath = className.replace('.', '/');
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File sourceDir = new File(jackknife, "source");

        // Find which jar contains this class
        File targetJar = null;
        Artifact targetArtifact = null;
        final Set<Artifact> artifacts = project.getArtifacts();
        for (final Artifact artifact : artifacts) {
            final File file = artifact.getFile();
            if (file == null || !file.exists() || !file.getName().endsWith(".jar")) {
                continue;
            }

            try (final java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
                if (jar.getEntry(classPath + ".class") != null) {
                    targetJar = file;
                    targetArtifact = artifact;
                    break;
                }
            } catch (final IOException e) {
                // Skip unreadable jars
            }
        }

        if (targetJar == null) {
            throw new MojoFailureException("Class not found in any indexed jar: " + className + "\n"
                    + "To index project dependencies:  mvn jackknife:index\n"
                    + "To search ~/.m2/repository:     mvn jackknife:index -Dclass=" + className);
        }

        final String groupId = targetArtifact.getGroupId();
        final String jarDirName = targetJar.getName().replace(".jar", "");
        final File jarSourceDir = new File(new File(sourceDir, groupId), jarDirName);

        // Check if this jar has already been decompiled
        final File requestedFile = new File(jarSourceDir, classPath + ".java");
        if (requestedFile.exists()) {
            getLog().info("Source available: " + requestedFile.getPath());
            printFile(requestedFile);
            return;
        }

        // Decompile the entire jar
        getLog().info("Decompiling " + targetArtifact.getGroupId() + ":" + targetArtifact.getArtifactId()
                + ":" + targetArtifact.getVersion() + " (" + targetJar.getName() + ")");

        jarSourceDir.mkdirs();
        final AtomicInteger count = new AtomicInteger(0);
        final PerClassSaver saver = new PerClassSaver(jarSourceDir, count);

        final Decompiler decompiler = Decompiler.builder()
                .inputs(targetJar)
                .output(saver)
                .option(IFernflowerPreferences.LOG_LEVEL, "error")
                .option(IFernflowerPreferences.THREADS, String.valueOf(Runtime.getRuntime().availableProcessors()))
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

        getLog().info("Decompiled " + count.get() + " classes to " + jarSourceDir.getPath());

        // Now print the requested class
        if (requestedFile.exists()) {
            printFile(requestedFile);
        } else {
            throw new MojoFailureException("Decompilation completed but " + className + " was not produced");
        }
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
     * IResultSaver that writes each decompiled class to its own .java file,
     * using the class's internal path as the file path within the output directory.
     */
    private static class PerClassSaver implements IResultSaver {

        private final File outputDir;
        private final AtomicInteger count;

        PerClassSaver(final File outputDir, final AtomicInteger count) {
            this.outputDir = outputDir;
            this.count = count;
        }

        @Override
        public void saveClassEntry(final String path, final String archiveName,
                                   final String qualifiedName, final String entryName,
                                   final String content) {
            writeClass(entryName, content);
        }

        @Override
        public void saveClassFile(final String path, final String qualifiedName,
                                  final String entryName, final String content, final int[] mapping) {
            writeClass(entryName, content);
        }

        private void writeClass(final String entryName, final String content) {
            if (content == null || entryName == null) {
                return;
            }

            final File outputFile = new File(outputDir, entryName);
            outputFile.getParentFile().mkdirs();

            try (final PrintWriter out = new PrintWriter(new FileWriter(outputFile))) {
                out.print(content);
                count.incrementAndGet();
            } catch (final IOException e) {
                // Skip classes we can't write
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
