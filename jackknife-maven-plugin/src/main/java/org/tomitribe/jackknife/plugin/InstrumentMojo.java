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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates instrumentation config files from a method specification.
 * Searches manifests to find which jar contains the target class,
 * then writes a properties file to .jackknife/instrument/.
 *
 * Usage:
 *   mvn jackknife:instrument -Dmethod="org.tomitribe.util.Join.join" -Dmode=debug
 *   mvn jackknife:instrument -Dmethod="join" -Dclass=org.tomitribe.util.Join -Dmode=timing
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File manifestDir = new File(jackknife, "manifest");
        final File instrumentDir = new File(jackknife, "instrument");

        if (!manifestDir.exists()) {
            throw new MojoFailureException("Manifests not found. Run 'mvn jackknife:index' first.");
        }

        // Parse the method specification
        final MethodParser.ParsedMethod parsed = MethodParser.parse(method, targetClass);
        if (parsed == null) {
            throw new MojoFailureException("Could not parse method specification: " + method);
        }

        if (!parsed.hasClass()) {
            throw new MojoFailureException("Class name required. Use -Dclass=com.example.Foo or include it in the method: "
                    + "com.example.Foo." + parsed.getMethodName());
        }

        final String className = parsed.getClassName();
        final String methodName = parsed.getMethodName();

        getLog().info("Instrumenting " + className + "." + methodName + " [" + mode + "]");

        // Search manifests to find which jar contains this class
        final List<ManifestMatch> manifestMatches = searchManifests(manifestDir, className);

        if (manifestMatches.isEmpty()) {
            throw new MojoFailureException("Class " + className + " not found in any manifest. "
                    + "Run 'mvn jackknife:index' to rebuild.");
        }

        // Build the Snitch-format method string
        final String snitchMethod = parsed.toSnitchFormat();
        final String prefix = "timing".equals(mode) ? "" : "@";
        final String propertyLine = prefix + snitchMethod + " = " + snitchMethod;

        // Write properties files for each matching artifact
        int filesWritten = 0;
        for (final ManifestMatch match : manifestMatches) {
            final File groupDir = new File(instrumentDir, match.groupId);
            groupDir.mkdirs();

            final File propsFile = new File(groupDir, match.artifactFileName + ".properties");

            // Read existing lines
            final Set<String> lines = new LinkedHashSet<>();
            if (propsFile.exists()) {
                try (final BufferedReader reader = new BufferedReader(new FileReader(propsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("#") && !line.isBlank()) {
                            lines.add(line);
                        }
                    }
                } catch (final IOException e) {
                    getLog().warn("Failed to read existing properties: " + propsFile);
                }
            }

            lines.add(propertyLine);

            try (final PrintWriter out = new PrintWriter(new FileWriter(propsFile))) {
                out.println("# Jackknife instrumentation config");
                out.println("# Mode: " + mode);
                out.println();
                for (final String line : lines) {
                    out.println(line);
                }
                filesWritten++;
            } catch (final IOException e) {
                throw new MojoExecutionException("Failed to write: " + propsFile, e);
            }

            getLog().info("  " + match.groupId + ":" + match.artifactFileName);
        }

        getLog().info("");
        getLog().info("Wrote " + filesWritten + " instrumentation config(s) to .jackknife/instrument/");
        getLog().info("Next build will apply instrumentation automatically.");
    }

    private List<ManifestMatch> searchManifests(final File manifestDir, final String className) {
        final List<ManifestMatch> matches = new ArrayList<>();

        final File[] groupDirs = manifestDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return matches;
        }

        for (final File groupDir : groupDirs) {
            final File[] manifestFiles = groupDir.listFiles(
                    (final File dir, final String name) -> name.endsWith(".manifest"));
            if (manifestFiles == null) {
                continue;
            }

            for (final File manifestFile : manifestFiles) {
                if (containsClass(manifestFile, className)) {
                    final String artifactFileName = manifestFile.getName()
                            .replace(".manifest", "");
                    matches.add(new ManifestMatch(groupDir.getName(), artifactFileName));
                }
            }
        }

        return matches;
    }

    private boolean containsClass(final File manifestFile, final String className) {
        try (final BufferedReader reader = new BufferedReader(new FileReader(manifestFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(className)) {
                    return true;
                }
            }
        } catch (final IOException e) {
            // skip
        }
        return false;
    }

    private record ManifestMatch(String groupId, String artifactFileName) {
    }
}
