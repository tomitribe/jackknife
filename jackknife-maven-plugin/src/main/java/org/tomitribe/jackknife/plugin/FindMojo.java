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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Finds which jar(s) contain a class or pattern.
 *
 * Usage: mvn jackknife:find -Dclass=com.example.MyClass
 *        mvn jackknife:find -Dquery=CustomField
 */
@Mojo(name = "find", aggregator = true)
public class FindMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "class")
    private String className;

    @Parameter(property = "query")
    private String query;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File indexDir = new File(rootDir, ".jackknife/index");

        if (!indexDir.exists()) {
            throw new MojoFailureException("Index not found. Run 'mvn jackknife:index' first.");
        }

        final String searchTerm;
        final boolean exactMatch;
        if (className != null && !className.isEmpty()) {
            searchTerm = "# " + className;
            exactMatch = true;
        } else if (query != null && !query.isEmpty()) {
            searchTerm = query;
            exactMatch = false;
        } else {
            throw new MojoFailureException("Specify -Dclass=com.example.Foo or -Dquery=searchterm");
        }

        boolean found = false;
        final File[] groupDirs = indexDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            throw new MojoFailureException("No index files found.");
        }

        for (final File groupDir : groupDirs) {
            final File[] indexFiles = groupDir.listFiles((final File dir, final String name) -> name.endsWith(".index"));
            if (indexFiles == null) {
                continue;
            }

            for (final File indexFile : indexFiles) {
                final String artifactName = indexFile.getName().replace(".index", "");
                final var matches = searchIndex(indexFile, searchTerm, exactMatch);

                if (!matches.isEmpty()) {
                    found = true;
                    System.out.println(groupDir.getName() + ":" + artifactName);
                    for (final String match : matches) {
                        System.out.println("  " + match);
                    }
                }
            }
        }

        if (!found) {
            final String term = className != null ? className : query;
            throw new MojoFailureException("No matches found for: " + term);
        }
    }

    private java.util.List<String> searchIndex(final File indexFile, final String searchTerm,
                                               final boolean exactMatch) {
        final java.util.List<String> matches = new java.util.ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (exactMatch) {
                    if (line.equals(searchTerm)) {
                        matches.add(line.substring(2)); // strip "# "
                    }
                } else {
                    if (line.contains(searchTerm)) {
                        matches.add(line.trim());
                    }
                }
            }
        } catch (final IOException e) {
            getLog().debug("Failed to read: " + indexFile);
        }

        return matches;
    }
}
