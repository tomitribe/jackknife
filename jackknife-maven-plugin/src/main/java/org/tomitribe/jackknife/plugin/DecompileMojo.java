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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
@Mojo(name = "decompile", requiresDependencyResolution = ResolutionScope.TEST, aggregator = true)
public class DecompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(defaultValue = "${settings.localRepository}", readonly = true)
    private String localRepository;

    @Parameter(property = "class", required = true)
    private String className;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String classPath = className.replace('.', '/');
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File sourceDir = new File(jackknife, "source");
        final File manifestDir = new File(jackknife, "manifest");

        // Step 1: search manifests (works for both single and multi-module)
        File targetJar = null;
        String groupId = null;

        if (manifestDir.exists()) {
            final ManifestResult result = findInManifests(manifestDir, className);
            if (result != null) {
                groupId = result.groupId;
                // Resolve the jar file from the local repo
                targetJar = resolveJarFromRepo(new File(localRepository), result.groupId, result.jarFileName);
                if (targetJar == null) {
                    // Try project artifacts as fallback
                    targetJar = findJarInArtifacts(result.jarFileName);
                }
            }
        }

        // Step 2: fall back to project artifacts (single-module, no index needed)
        if (targetJar == null) {
            final Set<Artifact> artifacts = project.getArtifacts();
            for (final Artifact artifact : artifacts) {
                final File file = artifact.getFile();
                if (file == null || !file.exists() || !file.getName().endsWith(".jar")) {
                    continue;
                }
                try (final java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
                    if (jar.getEntry(classPath + ".class") != null) {
                        targetJar = file;
                        groupId = artifact.getGroupId();
                        break;
                    }
                } catch (final IOException e) {
                    // Skip unreadable jars
                }
            }
        }

        if (targetJar == null) {
            throw new MojoFailureException("Class not found in any indexed jar: " + className + "\n"
                    + "To index project dependencies:  mvn jackknife:index\n"
                    + "To search ~/.m2/repository:     mvn jackknife:index -Dclass=" + className);
        }

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
        getLog().info("Decompiling " + groupId + ":" + targetJar.getName());

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

    /**
     * Search manifests for a class. Returns the groupId and jar filename if found.
     */
    private static ManifestResult findInManifests(final File manifestDir, final String className) {
        final File[] groupDirs = manifestDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return null;
        }

        for (final File groupDir : groupDirs) {
            final File[] manifests = groupDir.listFiles((final File d, final String n) -> n.endsWith(".manifest"));
            if (manifests == null) {
                continue;
            }

            for (final File manifest : manifests) {
                try (final BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals(className)) {
                            final String jarFileName = manifest.getName().replace(".manifest", "");
                            return new ManifestResult(groupDir.getName(), jarFileName);
                        }
                    }
                } catch (final IOException e) {
                    // skip
                }
            }
        }
        return null;
    }

    /**
     * Resolve a jar file from the local Maven repository given groupId and jar filename.
     */
    private static File resolveJarFromRepo(final File repoDir, final String groupId, final String jarFileName) {
        final String groupPath = groupId.replace('.', '/');
        final File groupDir = new File(repoDir, groupPath);
        if (!groupDir.exists()) {
            return null;
        }

        // Walk the groupId directory to find the jar
        final File[] files = groupDir.listFiles();
        if (files == null) {
            return null;
        }

        for (final File artifactDir : files) {
            if (!artifactDir.isDirectory()) {
                continue;
            }
            final File[] versionDirs = artifactDir.listFiles(File::isDirectory);
            if (versionDirs == null) {
                continue;
            }
            for (final File versionDir : versionDirs) {
                final File jar = new File(versionDir, jarFileName);
                if (jar.exists()) {
                    return jar;
                }
            }
        }
        return null;
    }

    /**
     * Search project artifacts for a jar with a specific filename.
     */
    private File findJarInArtifacts(final String jarFileName) {
        for (final Artifact artifact : project.getArtifacts()) {
            final File file = artifact.getFile();
            if (file != null && file.getName().equals(jarFileName)) {
                return file;
            }
        }
        return null;
    }

    private record ManifestResult(String groupId, String jarFileName) {
    }
}
