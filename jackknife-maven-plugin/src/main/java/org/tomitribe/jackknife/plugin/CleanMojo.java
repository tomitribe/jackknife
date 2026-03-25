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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Removes .jackknife/ or a sub-path within it.
 *
 * Usage:
 *   mvn jackknife:clean                              # remove all of .jackknife/
 *   mvn jackknife:clean -Dpath=modified               # remove .jackknife/modified/
 *   mvn jackknife:clean -Dpath=modified/org.tomitribe  # remove specific groupId
 *   mvn jackknife:clean -Dpath=source                  # clear decompile cache
 */
@Mojo(name = "clean")
public class CleanMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    private String executionRootDirectory;

    @Parameter(property = "path")
    private String path;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");

        if (!jackknife.exists()) {
            getLog().info("Nothing to clean — .jackknife/ does not exist");
            return;
        }

        final File target;
        if (path == null || path.isEmpty()) {
            target = jackknife;
        } else {
            validatePath(path);
            target = new File(jackknife, path);
        }

        // Final safety check: resolved path must be under .jackknife/
        try {
            final String canonicalTarget = target.getCanonicalPath();
            final String canonicalJackknife = jackknife.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalJackknife)) {
                throw new MojoFailureException("Path escapes .jackknife/: " + path);
            }
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to resolve path", e);
        }

        if (!target.exists()) {
            getLog().info("Nothing to clean — " + relativePath(jackknife, target) + " does not exist");
            return;
        }

        final long count = deleteRecursively(target);
        getLog().info("Cleaned " + relativePath(jackknife, target) + " (" + count + " files removed)");
    }

    private static void validatePath(final String path) throws MojoFailureException {
        if (path.contains("..")) {
            throw new MojoFailureException("Path must not contain '..': " + path);
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new MojoFailureException("Path must be relative: " + path);
        }
        if (path.contains(":")) {
            throw new MojoFailureException("Path must not contain ':': " + path);
        }
    }

    private static long deleteRecursively(final File root) {
        if (!root.exists()) {
            return 0;
        }

        long count = 0;
        try (final Stream<Path> walk = Files.walk(root.toPath())) {
            final var paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (final Path p : paths) {
                Files.deleteIfExists(p);
                if (Files.isRegularFile(p) || !p.toFile().exists()) {
                    count++;
                }
            }
        } catch (final IOException e) {
            // Best effort
        }
        return count;
    }

    private static String relativePath(final File base, final File target) {
        if (base.equals(target)) {
            return ".jackknife/";
        }
        return ".jackknife/" + base.toPath().relativize(target.toPath());
    }
}
