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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.tomitribe.jackknife.index.IndexSearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates instrumentation config files from a method specification.
 * Uses the index to resolve method-name-only targets across all matching classes.
 * Writes Snitch-compatible properties files to .jackknife/instrument/.
 *
 * Usage:
 *   mvn jackknife:instrument -Dmethod="com.example.Foo.process(String, int)" -Dmode=debug
 *   mvn jackknife:instrument -Dmethod="retryRequest" -Dmode=timing
 *   mvn jackknife:instrument -Dmethod="retryRequest" -Dimplements=HttpRequestRetryHandler
 */
@Mojo(name = "instrument", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class InstrumentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "method", required = true)
    private String method;

    @Parameter(property = "class")
    private String targetClass;

    @Parameter(property = "mode", defaultValue = "debug")
    private String mode;

    @Parameter(property = "implements")
    private String implementsFilter;

    @Parameter(property = "package")
    private String packageFilter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File indexDir = new File(jackknife, "index");
        final File instrumentDir = new File(jackknife, "instrument");

        if (!indexDir.exists()) {
            throw new MojoFailureException("Index not found. Run 'mvn jackknife:index' first.");
        }

        // Parse the method specification
        final MethodParser.ParsedMethod parsed = MethodParser.parse(method, targetClass);
        if (parsed == null) {
            throw new MojoFailureException("Could not parse method specification: " + method);
        }

        getLog().info("Searching for: " + parsed.getMethodName()
                + (parsed.hasClass() ? " in " + parsed.getClassName() : " (all classes)")
                + (implementsFilter != null ? " implements " + implementsFilter : "")
                + (packageFilter != null ? " in package " + packageFilter : ""));

        // Search the index
        final List<IndexSearch.Match> matches;
        try {
            matches = IndexSearch.search(indexDir, parsed, implementsFilter, packageFilter);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to search index", e);
        }

        if (matches.isEmpty()) {
            throw new MojoFailureException("No matching methods found. Check the method name and ensure the index is up to date.");
        }

        // Group matches by artifact
        final Map<String, Set<String>> linesByArtifact = new LinkedHashMap<>();
        final Map<String, String> artifactGroupIds = new LinkedHashMap<>();

        for (final IndexSearch.Match match : matches) {
            final String artifactFileName = match.getArtifactFileName();
            final Set<String> lines = linesByArtifact.computeIfAbsent(artifactFileName, k -> new LinkedHashSet<>());

            // Build the property line: key = value
            // Key is the monitor name (with @ prefix for debug/tracking mode)
            // Value is the Snitch-format method signature
            final String snitchMethod = match.getSnitchMethod();
            final String prefix = "timing".equals(mode) ? "" : "@";
            lines.add(prefix + snitchMethod + " = " + snitchMethod);

            // Track groupId from the index file path
            final File indexFile = new File(match.getIndexFile());
            final String groupId = indexFile.getParentFile().getName();
            artifactGroupIds.put(artifactFileName, groupId);
        }

        // Write properties files
        int filesWritten = 0;
        for (final Map.Entry<String, Set<String>> entry : linesByArtifact.entrySet()) {
            final String artifactFileName = entry.getKey();
            final Set<String> newLines = entry.getValue();
            final String groupId = artifactGroupIds.get(artifactFileName);

            final File groupDir = new File(instrumentDir, groupId);
            groupDir.mkdirs();

            final File propsFile = new File(groupDir, artifactFileName + ".properties");

            // Read existing lines if file exists
            final Set<String> existingLines = new LinkedHashSet<>();
            if (propsFile.exists()) {
                try (final BufferedReader reader = new BufferedReader(new FileReader(propsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("#") && !line.isBlank()) {
                            existingLines.add(line);
                        }
                    }
                } catch (final IOException e) {
                    getLog().warn("Failed to read existing properties: " + propsFile);
                }
            }

            existingLines.addAll(newLines);

            try (final PrintWriter out = new PrintWriter(new FileWriter(propsFile))) {
                out.println("# Jackknife instrumentation config");
                out.println("# Mode: " + mode);
                out.println("# Generated by: mvn jackknife:instrument -Dmethod=\"" + method + "\" -Dmode=" + mode);
                out.println();

                for (final String line : existingLines) {
                    out.println(line);
                }

                filesWritten++;
            } catch (final IOException e) {
                throw new MojoExecutionException("Failed to write properties file: " + propsFile, e);
            }
        }

        // Print summary
        getLog().info("");
        getLog().info("Instrumented " + matches.size() + " method(s) across " + filesWritten + " artifact(s):");
        for (final IndexSearch.Match match : matches) {
            getLog().info("  " + match.getSnitchMethod() + " [" + mode + "]");
        }
        getLog().info("");
        getLog().info("Properties written to .jackknife/instrument/");
        getLog().info("Next build will apply instrumentation automatically.");
    }
}
