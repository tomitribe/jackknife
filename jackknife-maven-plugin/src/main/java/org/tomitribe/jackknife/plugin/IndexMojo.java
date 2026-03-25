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
@Mojo(name = "index", requiresDependencyResolution = ResolutionScope.TEST)
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
            out.println("# Jackknife");
            out.println();
            out.println("Two capabilities for working with Java dependencies:");
            out.println();
            out.println("1. **Explore** — Find classes, read decompiled source, understand APIs.");
            out.println("   No more digging through ~/.m2/repository or guessing at method signatures.");
            out.println("2. **Debug** — Instrument methods to capture arguments, return values,");
            out.println("   exceptions, and timing as structured JSON. No more adding println");
            out.println("   statements to tests.");
            out.println();
            out.println("## Exploring Dependencies");
            out.println();
            out.println("### Find a class");
            out.println();
            out.println("Manifests list every class in every dependency jar. One class name per line.");
            out.println();
            out.println("```");
            out.println("Grep \"CustomField\" .jackknife/manifest/");
            out.println("```");
            out.println();
            out.println("This tells you which jar contains the class and its full package name.");
            out.println();
            out.println("### Read source code");
            out.println();
            out.println("Check if the class has already been decompiled:");
            out.println();
            out.println("```");
            out.println("Glob .jackknife/source/**/<ClassName>.java");
            out.println("```");
            out.println();
            out.println("If found, read it directly. If not, decompile the entire jar (one-time, ~3-5s):");
            out.println();
            out.println("```");
            out.println("mvn jackknife:decompile -Dclass=com.example.MyClass");
            out.println("```");
            out.println();
            out.println("Every class in that jar is now available as a .java file. All subsequent");
            out.println("reads are direct file access — no Maven invocation needed.");
            out.println();
            out.println("### Find resources");
            out.println();
            out.println("```");
            out.println("Grep \"META-INF/services\" .jackknife/manifest/");
            out.println("```");
            out.println();
            out.println("### Directory structure");
            out.println();
            out.println("```");
            out.println(".jackknife/");
            out.println("├── manifest/            Class listings (all jars, sub-second)");
            out.println("│   └── <groupId>/");
            out.println("│       └── <artifact>-<version>.jar.manifest");
            out.println("├── source/              Decompiled source (per-jar, on demand)");
            out.println("│   └── <groupId>/");
            out.println("│       └── <artifact>-<version>/");
            out.println("│           └── com/example/MyClass.java");
            out.println("├── instrument/          Pending instrumentation configs");
            out.println("├── modified/            Patched jars (applied on next build)");
            out.println("└── USAGE.md             This file");
            out.println("```");
            out.println();
            out.println("## Debugging with Instrumentation");
            out.println();
            out.println("### The workflow");
            out.println();
            out.println("1. A test fails or you need to understand what a method receives and returns");
            out.println("2. Instrument the method — jackknife injects debug output, no source changes");
            out.println("3. Run `mvn test` — structured JSON output shows exactly what happened");
            out.println("4. Extract values from the JSON to fix assertions or understand behavior");
            out.println("5. Clean up when done: `mvn jackknife:clean -Dpath=modified`");
            out.println();
            out.println("### Instrument a method");
            out.println();
            out.println("```");
            out.println("mvn jackknife:instrument -Dmethod=\"com.example.Foo.bar(java.lang.String,int)\"");
            out.println("```");
            out.println();
            out.println("### Matching granularity");
            out.println();
            out.println("| Input | What gets instrumented |");
            out.println("|-------|----------------------|");
            out.println("| `com.example.Foo.bar(String,int)` | That one method |");
            out.println("| `com.example.Foo.bar` | All overloads of bar |");
            out.println("| `bar(String,int)` | bar(String,int) in any class |");
            out.println();
            out.println("### Modes");
            out.println();
            out.println("- **debug** (default) — args, return value, exceptions, and timing");
            out.println("- **timing** — elapsed time and status only, no args or return values");
            out.println();
            out.println("```");
            out.println("mvn jackknife:instrument -Dmethod=\"com.example.Foo.bar\" -Dmode=timing");
            out.println("```");
            out.println();
            out.println("### Run the build");
            out.println();
            out.println("```");
            out.println("mvn test");
            out.println("```");
            out.println();
            out.println("The next build automatically applies the instrumentation. No special flags.");
            out.println();
            out.println("### Reading the output");
            out.println();
            out.println("Every instrumented call produces one line prefixed with `JACKKNIFE`:");
            out.println();
            out.println("```");
            out.println("JACKKNIFE {\"event\":\"register\",\"mode\":\"debug\",\"method\":\"org.tomitribe.util.Join.join\"}");
            out.println("JACKKNIFE {\"event\":\"call\",\"time\":\"12.3us\",\"class\":\"Join\",\"method\":\"join\",\"args\":[\", \",[\"x\",\"y\",\"z\"]],\"return\":\"x, y, z\"}");
            out.println("```");
            out.println();
            out.println("The register line confirms instrumentation is active. If you see the");
            out.println("register event but no call events, the method was never called by your test.");
            out.println();
            out.println("**Grep for all instrumented calls:**");
            out.println();
            out.println("```");
            out.println("Grep \"JACKKNIFE\" target/surefire-reports/");
            out.println("```");
            out.println();
            out.println("### JSON fields");
            out.println();
            out.println("| Field | Description |");
            out.println("|-------|------------|");
            out.println("| `event` | `\"register\"` or `\"call\"` |");
            out.println("| `time` | Elapsed time (ns, us, ms, s) |");
            out.println("| `class` | Simple class name |");
            out.println("| `method` | Method name |");
            out.println("| `args` | JSON array of argument values |");
            out.println("| `return` | Return value (absent on exception) |");
            out.println("| `exception` | `{\"type\":\"...\",\"message\":\"...\"}` (absent on success) |");
            out.println("| `status` | `\"returned\"` or `\"thrown\"` (on file-reference lines) |");
            out.println("| `file` | Capture file path (when values too large for one line) |");
            out.println();
            out.println("### Extracting values for assertions");
            out.println();
            out.println("Strings are JSON-quoted, numbers are bare, null is `null`, booleans are bare.");
            out.println("Copy values directly from the `\"return\"` field into assertEquals:");
            out.println();
            out.println("```java");
            out.println("// From output: \"return\":\"a and b and c\"");
            out.println("assertEquals(\"a and b and c\", result);");
            out.println();
            out.println("// From output: \"return\":42");
            out.println("assertEquals(42, result);");
            out.println();
            out.println("// From output: \"return\":null");
            out.println("assertNull(result);");
            out.println("```");
            out.println();
            out.println("### Capture files");
            out.println();
            out.println("When arguments or return values are too large for a single line, the full");
            out.println("JSON event is written to a capture file:");
            out.println();
            out.println("```");
            out.println("JACKKNIFE {\"event\":\"call\",\"time\":\"2.3ms\",\"class\":\"Join\",\"method\":\"join\",\"status\":\"returned\",\"file\":\"target/jackknife/captures/capture-0012.txt\"}");
            out.println("```");
            out.println();
            out.println("The console line shows timing, method, and status for quick scanning.");
            out.println("Read the capture file for the complete JSON event with all argument");
            out.println("and return values.");
            out.println();
            out.println("### Cleaning up");
            out.println();
            out.println("```");
            out.println("mvn jackknife:clean                                # remove all of .jackknife/");
            out.println("mvn jackknife:clean -Dpath=modified                # stop instrumentation, keep manifests");
            out.println("mvn jackknife:clean -Dpath=modified/org.tomitribe  # remove one groupId's patches");
            out.println("mvn jackknife:clean -Dpath=source                  # clear decompile cache");
            out.println("```");
            out.println();
            out.println("After cleaning modified/, the next build uses original unmodified dependencies.");
            out.println("Manifests and decompiled source are cheap to regenerate: `mvn jackknife:index`");
        }

        getLog().info("Generated " + usageFile.getPath());
    }
}
