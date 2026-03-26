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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Searches ~/.m2/repository for classes and jars.
 */
final class RepoSearch {

    private RepoSearch() {
    }

    /**
     * Find a specific class in the local Maven repository.
     *
     * Algorithm:
     * 1. Derive search root from class package (first 2 segments)
     * 2. Walk from there, collecting all jars
     * 3. Group by groupId:artifactId, keep latest version only
     * 4. Sort by affinity (longest match of repo path to class package)
     * 5. Check each jar for the class entry
     * 6. Return first hit
     */
    static File findClassInRepo(final File repoDir, final String className) throws IOException {
        final String classPath = className.replace('.', '/') + ".class";
        final String packageName = className.contains(".")
                ? className.substring(0, className.lastIndexOf('.'))
                : "";

        // Derive search root from first 2 segments of the package
        final File searchRoot = deriveSearchRoot(repoDir, packageName);
        if (!searchRoot.exists()) {
            return null;
        }

        // Collect all jars under the search root
        final List<File> jars = collectJars(searchRoot);
        if (jars.isEmpty()) {
            return null;
        }

        // Group by artifact (groupId:artifactId), keep latest version
        final List<File> latestJars = keepLatestVersions(jars, repoDir);

        // Sort by affinity to the package name
        latestJars.sort(affinityComparator(repoDir, packageName));

        // Check each jar for the class
        for (final File jar : latestJars) {
            if (containsClass(jar, classPath)) {
                return jar;
            }
        }

        return null;
    }

    /**
     * Find jars matching a glob filter across the entire repo.
     * Returns latest version of each artifact only.
     */
    static List<File> findJarsByFilter(final File repoDir, final String filter) throws IOException {
        final Pattern pattern = globToPattern(filter);
        final List<File> allJars = collectJars(repoDir);

        // Filter by path pattern
        final List<File> matched = new ArrayList<>();
        for (final File jar : allJars) {
            final String relativePath = repoDir.toPath().relativize(jar.toPath()).toString();
            if (pattern.matcher(relativePath).matches()) {
                matched.add(jar);
            }
        }

        return keepLatestVersions(matched, repoDir);
    }

    /**
     * Derive search root from first 2 segments of the package.
     * com.fasterxml.jackson.databind -> repo/com/fasterxml/
     */
    static File deriveSearchRoot(final File repoDir, final String packageName) {
        final String[] segments = packageName.split("\\.");
        if (segments.length < 2) {
            return repoDir;
        }
        return new File(repoDir, segments[0] + "/" + segments[1]);
    }

    /**
     * Walk a directory tree collecting all .jar files.
     */
    private static List<File> collectJars(final File root) throws IOException {
        final List<File> jars = new ArrayList<>();
        if (!root.exists()) {
            return jars;
        }

        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                if (file.toString().endsWith(".jar") && !file.toString().contains("-sources")
                        && !file.toString().contains("-javadoc") && !file.toString().contains("-tests")) {
                    jars.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return jars;
    }

    /**
     * Group jars by artifact identity (everything except version),
     * keep only the latest version of each.
     */
    static List<File> keepLatestVersions(final List<File> jars, final File repoDir) {
        // Key: groupId:artifactId, Value: best jar
        final Map<String, File> best = new LinkedHashMap<>();
        final Map<String, Version> bestVersions = new LinkedHashMap<>();

        for (final File jar : jars) {
            final ArtifactInfo info = parseArtifactInfo(jar, repoDir);
            if (info == null) {
                continue;
            }

            final String key = info.groupId + ":" + info.artifactId;
            final Version version = new Version(info.version);

            if (!best.containsKey(key) || version.compareTo(bestVersions.get(key)) > 0) {
                best.put(key, jar);
                bestVersions.put(key, version);
            }
        }

        return new ArrayList<>(best.values());
    }

    /**
     * Parse artifact info from the jar's path in the repo.
     * repo/com/fasterxml/jackson/core/jackson-core/2.15.3/jackson-core-2.15.3.jar
     * -> groupId=com.fasterxml.jackson.core, artifactId=jackson-core, version=2.15.3
     */
    static ArtifactInfo parseArtifactInfo(final File jar, final File repoDir) {
        final Path relative = repoDir.toPath().relativize(jar.toPath());
        final int count = relative.getNameCount();

        // Need at least: groupId-segment / artifactId / version / file.jar
        if (count < 4) {
            return null;
        }

        final String version = relative.getName(count - 2).toString();
        final String artifactId = relative.getName(count - 3).toString();

        final StringBuilder groupId = new StringBuilder();
        for (int i = 0; i < count - 3; i++) {
            if (i > 0) groupId.append('.');
            groupId.append(relative.getName(i));
        }

        return new ArtifactInfo(groupId.toString(), artifactId, version);
    }

    /**
     * Sort by longest match of the repo path (groupId + artifactId) to the target package.
     */
    private static Comparator<File> affinityComparator(final File repoDir, final String packageName) {
        return (final File a, final File b) -> {
            final int affinityA = computeAffinity(a, repoDir, packageName);
            final int affinityB = computeAffinity(b, repoDir, packageName);
            return Integer.compare(affinityB, affinityA); // higher affinity first
        };
    }

    private static int computeAffinity(final File jar, final File repoDir, final String packageName) {
        final ArtifactInfo info = parseArtifactInfo(jar, repoDir);
        if (info == null) {
            return 0;
        }

        // Count matching characters between (groupId + "." + artifactId) and package name
        final String artifactPath = info.groupId + "." + info.artifactId;
        final int limit = Math.min(artifactPath.length(), packageName.length());
        int matched = 0;
        for (int i = 0; i < limit; i++) {
            if (artifactPath.charAt(i) == packageName.charAt(i)) {
                matched++;
            } else {
                break;
            }
        }
        return matched;
    }

    private static boolean containsClass(final File jar, final String classPath) {
        try (final JarFile jf = new JarFile(jar)) {
            return jf.getEntry(classPath) != null;
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * Convert a simple glob pattern to a regex.
     * ** matches any path, * matches one segment.
     */
    static Pattern globToPattern(final String glob) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            final char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++; // skip second *
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (".()[]{}+^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.toString());
    }

    record ArtifactInfo(String groupId, String artifactId, String version) {
    }
}
