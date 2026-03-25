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
 * Shows a class's structural information from the index — fields, methods,
 * annotations, hierarchy, exceptions — without decompiling.
 *
 * Faster than decompile, no Vineflower needed, and often sufficient
 * for understanding an API.
 *
 * Usage: mvn jackknife:describe -Dclass=com.example.MyClass
 */
@Mojo(name = "describe", aggregator = true)
public class DescribeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "class", required = true)
    private String className;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File indexDir = new File(rootDir, ".jackknife/index");

        if (!indexDir.exists()) {
            throw new MojoFailureException("Index not found. Run 'mvn jackknife:index' first.");
        }

        final String header = "# " + className;
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
                final String result = findClassInIndex(indexFile, header);
                if (result != null) {
                    final String artifactName = indexFile.getName().replace(".index", "");
                    System.out.println("# " + className + "  [" + groupDir.getName() + ":" + artifactName + "]");
                    System.out.println(result);
                    found = true;
                }
            }
        }

        if (!found) {
            throw new MojoFailureException("Class not found in index: " + className
                    + ". Run 'mvn jackknife:index' to rebuild.");
        }
    }

    /**
     * Find a class block in an index file. Returns the block content
     * (everything after the header until the next blank line or header),
     * or null if not found.
     */
    private String findClassInIndex(final File indexFile, final String header) {
        try (final BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            String line;
            boolean inBlock = false;
            final StringBuilder block = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (inBlock) {
                    // End of block: blank line or next class header
                    if (line.isEmpty() || (line.startsWith("# ") && !line.equals(header))) {
                        return block.toString();
                    }
                    block.append(line).append("\n");
                } else if (line.equals(header)) {
                    inBlock = true;
                }
            }

            if (inBlock) {
                return block.toString();
            }
        } catch (final IOException e) {
            getLog().debug("Failed to read index file: " + indexFile);
        }

        return null;
    }
}
