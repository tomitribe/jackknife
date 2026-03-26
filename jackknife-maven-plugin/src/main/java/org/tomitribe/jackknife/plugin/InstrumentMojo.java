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
import java.util.regex.Pattern;

/**
 * Creates instrumentation config files from a class and method specification.
 * Searches manifests to find which jar contains the target class(es),
 * then writes properties files to .jackknife/instrument/.
 *
 * Usage:
 *   mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod=join
 *   mvn jackknife:instrument -Dclass="org.junit.**" -Dmethod=assertEquals
 *   mvn jackknife:instrument -Dclass=org.junit.Assert
 *   mvn jackknife:instrument -Dmethod="org.tomitribe.util.Join.join(java.lang.String,java.util.Collection)"
 */
@Mojo(name = "instrument", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class InstrumentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "method")
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

        // Parse the method specification if provided
        final String className;
        final String methodSpec; // methodName, or methodName(args), or *

        if (method != null && !method.isBlank()) {
            final MethodParser.ParsedMethod parsed = MethodParser.parse(method, targetClass);
            if (parsed == null) {
                throw new MojoFailureException("Could not parse method specification: " + method);
            }

            if (!parsed.hasClass() && (targetClass == null || targetClass.isBlank())) {
                throw new MojoFailureException("Class name required. Use -Dclass=com.example.Foo or include it in -Dmethod");
            }

            className = parsed.hasClass() ? parsed.getClassName() : targetClass;
            // Preserve args if specified: "join(String,Collection)" vs just "join"
            if (parsed.hasArgs()) {
                methodSpec = parsed.getMethodName() + "(" + String.join(",", parsed.getArgTypes()) + ")";
            } else {
                methodSpec = parsed.getMethodName();
            }
        } else if (targetClass != null && !targetClass.isBlank()) {
            // -Dclass only, no -Dmethod — instrument all methods
            className = targetClass;
            methodSpec = "*";
        } else {
            throw new MojoFailureException("At least -Dclass is required.\n"
                    + "Examples:\n"
                    + "  mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar\n"
                    + "  mvn jackknife:instrument -Dclass=com.example.Foo\n"
                    + "  mvn jackknife:instrument -Dclass=\"org.junit.**\" -Dmethod=assertEquals");
        }

        // Check if class pattern contains wildcards
        final boolean isWildcard = className.contains("*");

        if (isWildcard) {
            if (!manifestDir.exists()) {
                throw new MojoFailureException("Manifests not found. Run:  mvn jackknife:index");
            }
            executeWildcard(manifestDir, instrumentDir, rootDir, className, methodSpec);
        } else {
            executeSingle(manifestDir, instrumentDir, rootDir, className, methodSpec);
        }

        getLog().info("Next build will apply instrumentation automatically.");
    }

    private void executeSingle(final File manifestDir, final File instrumentDir, final File rootDir,
                               final String className, final String methodSpec) throws MojoExecutionException, MojoFailureException {
        final String displayMethod = "*".equals(methodSpec) ? className + " (all methods)" : className + "." + methodSpec;
        getLog().info("Instrumenting " + displayMethod + " [" + mode + "]");

        final String propertyLine = buildPropertyLine(className, methodSpec);

        if (manifestDir.exists()) {
            final List<ManifestMatch> matches = searchManifests(manifestDir, className);
            if (!matches.isEmpty()) {
                int filesWritten = 0;
                for (final ManifestMatch match : matches) {
                    filesWritten += writeInstrumentConfig(instrumentDir, match.groupId,
                            match.artifactFileName + ".properties", propertyLine);
                    getLog().info("  " + match.groupId + ":" + match.artifactFileName);
                }
                getLog().info("Wrote " + filesWritten + " instrumentation config(s) to .jackknife/instrument/");
                return;
            }
        }

        if (isProjectClass(rootDir, className)) {
            writeInstrumentConfig(instrumentDir, "_project", "project.properties", propertyLine);
            getLog().info("  project code: " + className);
            getLog().info("Wrote instrumentation config to .jackknife/instrument/_project/");
            return;
        }

        throw new MojoFailureException("Class " + className + " not found in any manifest or project source.\n"
                + "To rebuild manifests:           mvn jackknife:index\n"
                + "To search ~/.m2/repository:     mvn jackknife:index -Dclass=" + className);
    }

    private void executeWildcard(final File manifestDir, final File instrumentDir, final File rootDir,
                                 final String classPattern, final String methodSpec) throws MojoExecutionException {
        final String displayMethod = "*".equals(methodSpec) ? " (all methods)" : "." + methodSpec;
        getLog().info("Instrumenting " + classPattern + displayMethod + " [" + mode + "]");

        final Pattern pattern = classGlobToPattern(classPattern);
        final List<ClassMatch> classMatches = searchManifestsWithPattern(manifestDir, pattern);

        // Also check project source
        final List<String> projectClasses = searchProjectSourceWithPattern(rootDir, pattern);

        if (classMatches.isEmpty() && projectClasses.isEmpty()) {
            getLog().warn("No classes matching pattern: " + classPattern);
            return;
        }

        // Write configs for dependency classes
        int totalConfigs = 0;
        for (final ClassMatch match : classMatches) {
            final String propertyLine = buildPropertyLine(match.className, methodSpec);
            writeInstrumentConfig(instrumentDir, match.groupId,
                    match.artifactFileName + ".properties", propertyLine);
            totalConfigs++;
        }

        // Write configs for project classes
        for (final String projectClass : projectClasses) {
            final String propertyLine = buildPropertyLine(projectClass, methodSpec);
            writeInstrumentConfig(instrumentDir, "_project", "project.properties", propertyLine);
            totalConfigs++;
        }

        getLog().info("Matched " + (classMatches.size() + projectClasses.size()) + " class(es), wrote " + totalConfigs + " config(s)");
    }

    private String buildPropertyLine(final String className, final String methodName) {
        final String prefix = "timing".equals(mode) ? "" : "@";
        final String spec = className + "." + methodName;
        return prefix + spec + " = " + spec;
    }

    // ---- Manifest search ----

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
                    final String artifactFileName = manifestFile.getName().replace(".manifest", "");
                    matches.add(new ManifestMatch(groupDir.getName(), artifactFileName));
                }
            }
        }

        return matches;
    }

    private List<ClassMatch> searchManifestsWithPattern(final File manifestDir, final Pattern pattern) {
        final List<ClassMatch> matches = new ArrayList<>();

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
                final String artifactFileName = manifestFile.getName().replace(".manifest", "");
                final List<String> matched = findMatchingClasses(manifestFile, pattern);
                for (final String className : matched) {
                    matches.add(new ClassMatch(groupDir.getName(), artifactFileName, className));
                }
            }
        }

        return matches;
    }

    private List<String> searchProjectSourceWithPattern(final File rootDir, final Pattern pattern) {
        final List<String> matches = new ArrayList<>();
        final String[] sourceRoots = {"src/main/java", "src/test/java"};

        for (final String root : sourceRoots) {
            final File sourceRoot = new File(rootDir, root);
            if (!sourceRoot.exists()) {
                continue;
            }
            collectMatchingSourceClasses(sourceRoot, sourceRoot, pattern, matches);
        }

        return matches;
    }

    private void collectMatchingSourceClasses(final File root, final File dir, final Pattern pattern,
                                              final List<String> matches) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (final File file : files) {
            if (file.isDirectory()) {
                collectMatchingSourceClasses(root, file, pattern, matches);
            } else if (file.getName().endsWith(".java")) {
                final String relative = root.toPath().relativize(file.toPath()).toString();
                final String className = relative.replace('/', '.').replace(".java", "");
                if (pattern.matcher(className).matches()) {
                    matches.add(className);
                }
            }
        }
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

    private List<String> findMatchingClasses(final File manifestFile, final Pattern pattern) {
        final List<String> matches = new ArrayList<>();
        try (final BufferedReader reader = new BufferedReader(new FileReader(manifestFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    break; // resources section
                }
                if (!line.isBlank() && pattern.matcher(line).matches()) {
                    matches.add(line);
                }
            }
        } catch (final IOException e) {
            // skip
        }
        return matches;
    }

    // ---- Config writing ----

    private int writeInstrumentConfig(final File instrumentDir, final String subDir,
                                       final String fileName, final String propertyLine)
            throws MojoExecutionException {
        final File dir = new File(instrumentDir, subDir);
        dir.mkdirs();

        final File propsFile = new File(dir, fileName);

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
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to write: " + propsFile, e);
        }
        return 1;
    }

    // ---- Utilities ----

    private static boolean isProjectClass(final File rootDir, final String className) {
        final String sourcePath = className.replace('.', '/') + ".java";
        final String[] sourceRoots = {"src/main/java", "src/test/java"};

        for (final String root : sourceRoots) {
            final File sourceFile = new File(rootDir, root + "/" + sourcePath);
            if (sourceFile.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert a class name glob to a regex pattern.
     * * matches one package level (no dots), ** matches any depth.
     */
    static Pattern classGlobToPattern(final String glob) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            final char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^.]*");
                }
            } else if (c == '?') {
                regex.append("[^.]");
            } else if (".()[]{}+^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.toString());
    }

    private record ManifestMatch(String groupId, String artifactFileName) {
    }

    private record ClassMatch(String groupId, String artifactFileName, String className) {
    }
}
