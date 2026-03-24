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
package org.tomitribe.jackknife.index;

import org.tomitribe.jackknife.plugin.MethodParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches index files for methods matching a parsed method specification.
 */
public final class IndexSearch {

    private IndexSearch() {
    }

    /**
     * A match found in the index.
     */
    public static final class Match {

        private final String indexFile;
        private final String className;
        private final String snitchMethod;
        private final String artifactFileName;

        public Match(final String indexFile, final String className, final String snitchMethod, final String artifactFileName) {
            this.indexFile = indexFile;
            this.className = className;
            this.snitchMethod = snitchMethod;
            this.artifactFileName = artifactFileName;
        }

        public String getIndexFile() {
            return indexFile;
        }

        public String getClassName() {
            return className;
        }

        public String getSnitchMethod() {
            return snitchMethod;
        }

        public String getArtifactFileName() {
            return artifactFileName;
        }
    }

    /**
     * Search all index files under the given directory for methods matching the parsed method.
     */
    public static List<Match> search(final File indexDir, final MethodParser.ParsedMethod parsed,
                                     final String implementsFilter, final String packageFilter) throws IOException {
        final List<Match> matches = new ArrayList<>();

        if (!indexDir.exists() || !indexDir.isDirectory()) {
            return matches;
        }

        // Walk all .index files
        final File[] groupDirs = indexDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return matches;
        }

        for (final File groupDir : groupDirs) {
            final File[] indexFiles = groupDir.listFiles((final File dir, final String name) -> name.endsWith(".index"));
            if (indexFiles == null) {
                continue;
            }

            for (final File indexFile : indexFiles) {
                // Derive artifact file name: remove .index suffix
                final String artifactFileName = indexFile.getName().substring(0, indexFile.getName().length() - ".index".length());
                searchIndexFile(indexFile, parsed, implementsFilter, packageFilter, artifactFileName, matches);
            }
        }

        return matches;
    }

    private static void searchIndexFile(final File indexFile, final MethodParser.ParsedMethod parsed,
                                        final String implementsFilter, final String packageFilter,
                                        final String artifactFileName, final List<Match> matches) throws IOException {
        String currentClass = null;
        boolean currentClassMatches = false;
        final List<String> currentInterfaces = new ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Class header
                if (line.startsWith("# ") && !line.equals("# resources")) {
                    // Save current class name
                    currentClass = line.substring(2).trim();
                    currentInterfaces.clear();

                    // Check class-level filters
                    currentClassMatches = true;

                    if (parsed.hasClass() && !currentClass.equals(parsed.getClassName())) {
                        currentClassMatches = false;
                    }

                    if (packageFilter != null && !currentClass.startsWith(packageFilter)) {
                        currentClassMatches = false;
                    }

                    continue;
                }

                // Track interfaces for implements filter
                if (line.startsWith("implements ")) {
                    currentInterfaces.add(line.substring("implements ".length()).trim());

                    // Apply implements filter
                    if (implementsFilter != null && currentClassMatches) {
                        currentClassMatches = currentInterfaces.stream()
                                .anyMatch(iface -> iface.contains(implementsFilter));
                    }
                    continue;
                }

                // Method lines
                if (currentClassMatches && currentClass != null && line.contains(" method ")) {
                    final String methodName = extractMethodName(line);
                    if (methodName == null) {
                        continue;
                    }

                    // Check method name match
                    if (!methodName.equals(parsed.getMethodName())) {
                        continue;
                    }

                    // If args specified, check arg types match
                    if (parsed.hasArgs()) {
                        final List<String> lineArgTypes = extractArgTypes(line);
                        if (!argsMatch(parsed.getArgTypes(), lineArgTypes)) {
                            continue;
                        }
                    }

                    // Build Snitch-format method string
                    final String snitchArgs = extractSnitchArgs(line);
                    final String snitchMethod = currentClass + "." + methodName + "(" + snitchArgs + ")";

                    matches.add(new Match(indexFile.getPath(), currentClass, snitchMethod, artifactFileName));
                }

                // Re-check implements filter when we see all interfaces
                if (implementsFilter != null && currentClassMatches && !line.startsWith("implements ") && !line.isEmpty()) {
                    if (!currentInterfaces.isEmpty()) {
                        currentClassMatches = currentInterfaces.stream()
                                .anyMatch(iface -> iface.contains(implementsFilter));
                    } else if (!line.startsWith("@") && !line.startsWith("class ") && !line.startsWith("public ")
                            && !line.startsWith("abstract ") && !line.startsWith("interface ") && !line.startsWith("enum ")) {
                        // Past the header section, if we haven't matched implements, skip this class
                        currentClassMatches = false;
                    }
                }
            }
        }
    }

    /**
     * Extract method name from an index method line.
     * Line format: "  public method process(java.lang.String input, int count) : boolean"
     */
    private static String extractMethodName(final String line) {
        final int methodKeyword = line.indexOf(" method ");
        if (methodKeyword < 0) {
            return null;
        }

        final int nameStart = methodKeyword + " method ".length();
        final int parenOrEnd = line.indexOf('(', nameStart);

        if (parenOrEnd < 0) {
            return line.substring(nameStart).trim();
        }

        return line.substring(nameStart, parenOrEnd).trim();
    }

    /**
     * Extract argument types from an index method line, stripping parameter names.
     */
    private static List<String> extractArgTypes(final String line) {
        final List<String> types = new ArrayList<>();
        final int parenOpen = line.indexOf('(');
        final int parenClose = line.lastIndexOf(')');

        if (parenOpen < 0 || parenClose < 0 || parenClose <= parenOpen + 1) {
            return types;
        }

        final String argsStr = line.substring(parenOpen + 1, parenClose);
        if (argsStr.isBlank()) {
            return types;
        }

        for (final String arg : argsStr.split(",")) {
            final String trimmed = arg.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // First token is the type
            final String[] tokens = trimmed.split("\\s+");
            types.add(tokens[0]);
        }

        return types;
    }

    /**
     * Extract argument types as a comma-separated string in Snitch format (types only, no names).
     */
    private static String extractSnitchArgs(final String line) {
        final List<String> types = extractArgTypes(line);
        return String.join(",", types);
    }

    private static boolean argsMatch(final List<String> expected, final List<String> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            // Allow simple name matching (String matches java.lang.String)
            final String exp = expected.get(i);
            final String act = actual.get(i);
            if (!exp.equals(act) && !act.endsWith("." + exp) && !exp.endsWith("." + act)) {
                return false;
            }
        }
        return true;
    }
}
