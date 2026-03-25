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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds lightweight manifests for all resolved dependencies — just class
 * names and resource paths, no ASM scanning, no decompilation.
 *
 * Sub-second for an entire classpath. Enough to answer "which jar has
 * this class?" and "what classes are in this jar?"
 *
 * Full decompilation happens on demand per-jar when a class is first
 * requested via the decompile goal.
 *
 * Also generates USAGE.md.
 */
@Mojo(name = "index", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class IndexMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File manifestDir = new File(jackknife, "manifest");

        manifestDir.mkdirs();

        final Set<Artifact> artifacts = project.getArtifacts();
        int indexed = 0;
        int skipped = 0;

        for (final Artifact artifact : artifacts) {
            final File file = artifact.getFile();

            if (file == null || !file.exists() || !file.getName().endsWith(".jar")) {
                skipped++;
                continue;
            }

            // Skip the project's own output artifact (uber jar awareness)
            if (artifact.getGroupId().equals(project.getGroupId())
                    && artifact.getArtifactId().equals(project.getArtifactId())) {
                skipped++;
                continue;
            }

            final String groupId = artifact.getGroupId();
            final String fileName = file.getName();

            final File groupDir = new File(manifestDir, groupId);
            final File manifestFile = new File(groupDir, fileName + ".manifest");

            // SNAPSHOT invalidation: re-manifest if jar is newer
            if (manifestFile.exists() && manifestFile.lastModified() >= file.lastModified()) {
                skipped++;
                continue;
            }

            try {
                writeManifest(file, manifestFile);
                indexed++;
            } catch (final IOException e) {
                getLog().warn("Failed to manifest " + file.getName() + ": " + e.getMessage());
                skipped++;
            }
        }

        getLog().info("Manifested " + indexed + " jars, skipped " + skipped + " (up to date or non-jar)");

        try {
            writeUsage(jackknife);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to write USAGE.md", e);
        }
    }

    /**
     * Write a lightweight manifest: one class name or resource path per line.
     * Just reads the zip directory — no decompression, no ASM.
     */
    private void writeManifest(final File jarFile, final File manifestFile) throws IOException {
        manifestFile.getParentFile().mkdirs();

        try (final JarFile jar = new JarFile(jarFile);
             final PrintWriter out = new PrintWriter(new FileWriter(manifestFile))) {

            final var entries = jar.entries();
            boolean hasResources = false;
            final java.util.List<String> resources = new java.util.ArrayList<>();

            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                final String name = entry.getName();
                if (name.endsWith(".class")) {
                    if (name.endsWith("module-info.class") || name.endsWith("package-info.class")) {
                        continue;
                    }
                    out.println(name.replace('/', '.').replace(".class", ""));
                } else {
                    resources.add(name);
                }
            }

            if (!resources.isEmpty()) {
                out.println("# resources");
                for (final String resource : resources) {
                    out.println(resource);
                }
            }
        }
    }

    private void writeUsage(final File jackknife) throws IOException {
        final File usageFile = new File(jackknife, "USAGE.md");

        try (final PrintWriter out = new PrintWriter(new FileWriter(usageFile))) {
            out.println("<!-- DO NOT EDIT — generated by jackknife-maven-plugin. Regenerated on every `mvn jackknife:index`. -->");
            out.println();
            out.println("# Jackknife Usage");
            out.println();
            out.println("## Quick Start");
            out.println();
            out.println("Manifests have been generated in `.jackknife/manifest/` and list every class");
            out.println("and resource in each dependency jar. Use Grep to search them directly.");
            out.println();
            out.println("Decompiled source is generated on demand per-jar in `.jackknife/source/`.");
            out.println("Use the Read tool to access individual class files directly.");
            out.println();
            out.println("## Directory Structure");
            out.println();
            out.println("```");
            out.println(".jackknife/");
            out.println("├── manifest/                Lightweight class listings (all jars, sub-second)");
            out.println("│   └── <groupId>/");
            out.println("│       └── <artifact>-<version>.jar.manifest");
            out.println("├── source/                  Decompiled source (per-jar, on demand)");
            out.println("│   └── <groupId>/");
            out.println("│       └── <artifact>-<version>/");
            out.println("│           └── com/example/MyClass.java");
            out.println("├── instrument/              Instrumentation inbox");
            out.println("│   └── <groupId>/");
            out.println("│       └── <artifact>-<version>.jar.properties");
            out.println("├── modified/                Patched jars + receipts");
            out.println("│   └── <groupId>/");
            out.println("│       ├── <artifact>-<version>.jar");
            out.println("│       └── <artifact>-<version>.jar.properties");
            out.println("└── USAGE.md                 This file (regenerated, do not edit)");
            out.println("```");
            out.println();
            out.println("## Finding Classes");
            out.println();
            out.println("Manifests are one class name per line. Search with Grep:");
            out.println("```");
            out.println("grep -r 'CustomField' .jackknife/manifest/");
            out.println("```");
            out.println();
            out.println("Find resources:");
            out.println("```");
            out.println("grep -r 'META-INF/services' .jackknife/manifest/");
            out.println("```");
            out.println();
            out.println("## Reading Source");
            out.println();
            out.println("After running `mvn jackknife:decompile -Dclass=com.example.MyClass`,");
            out.println("the entire jar is decompiled. Read any class directly:");
            out.println("```");
            out.println("Read .jackknife/source/<groupId>/<artifact>-<version>/com/example/MyClass.java");
            out.println("```");
            out.println();
            out.println("Subsequent lookups for classes in the same jar need no Maven invocation.");
            out.println();
            out.println("## Instrumenting");
            out.println();
            out.println("```");
            out.println("mvn jackknife:instrument -Dmethod=\"com.example.Foo.process(String, int)\" -Dmode=debug");
            out.println("```");
            out.println();
            out.println("Modes: `debug` (args + return + exceptions), `timing` (elapsed time only).");
            out.println("Instrumented jars applied automatically on next build.");
            out.println("Remove from `.jackknife/modified/` to revert.");
        }

        getLog().info("Generated " + usageFile.getPath());
    }
}
