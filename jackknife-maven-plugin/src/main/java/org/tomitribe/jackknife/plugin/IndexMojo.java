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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
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

    @Parameter(defaultValue = "${settings.localRepository}", readonly = true)
    private String localRepository;

    /** Search for a specific class in ~/.m2/repository */
    @Parameter(property = "class")
    private String className;

    /** Widen search to the entire local repository */
    @Parameter(property = "scope")
    private String scope;

    /** Glob filter for repo-wide search */
    @Parameter(property = "filter")
    private String filter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File rootDir = new File(executionRootDirectory);
        final File jackknife = new File(rootDir, ".jackknife");
        final File manifestDir = new File(jackknife, "manifest");

        manifestDir.mkdirs();

        // Smart class search: mvn jackknife:index -Dclass=com.example.Foo
        if (className != null && !className.isEmpty()) {
            executeClassSearch(jackknife, manifestDir);
            return;
        }

        // Repo-wide search: mvn jackknife:index -Dscope=repo -Dfilter="*jackson*"
        if ("repo".equals(scope)) {
            executeRepoSearch(manifestDir);
            return;
        }

        // Default: index project dependencies
        executeProjectIndex(manifestDir);

        try {
            writeUsage(jackknife);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to write USAGE.md", e);
        }
    }

    private void executeProjectIndex(final File manifestDir) {
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
    }

    /**
     * Smart class search: find a class in ~/.m2/repository.
     * Checks existing manifests first, then walks the repo.
     */
    private void executeClassSearch(final File jackknife, final File manifestDir) throws MojoExecutionException {
        // Step 1: check existing manifests
        final String match = searchExistingManifests(manifestDir, className);
        if (match != null) {
            getLog().info("Found in existing manifest: " + match);
            return;
        }

        // Step 2: search ~/.m2/repository
        final File repoDir = new File(localRepository);
        if (!repoDir.exists()) {
            throw new MojoExecutionException("Local repository not found: " + repoDir);
        }

        getLog().info("Searching " + repoDir + " for " + className + "...");

        try {
            final File jar = RepoSearch.findClassInRepo(repoDir, className);
            if (jar == null) {
                throw new MojoExecutionException("Class " + className + " not found in " + repoDir);
            }

            // Index the found jar
            final RepoSearch.ArtifactInfo info = RepoSearch.parseArtifactInfo(jar, repoDir);
            if (info == null) {
                throw new MojoExecutionException("Could not parse artifact info from " + jar);
            }

            final File groupDir = new File(manifestDir, info.groupId());
            final File manifestFile = new File(groupDir, jar.getName() + ".manifest");
            writeManifest(jar, manifestFile);

            getLog().info("Found " + className + " in " + info.groupId() + ":" + info.artifactId() + ":" + info.version());
            getLog().info("Indexed " + jar.getName() + " → " + manifestFile.getPath());

            try {
                writeUsage(jackknife);
            } catch (final IOException e) {
                // non-fatal
            }
        } catch (final IOException e) {
            throw new MojoExecutionException("Error searching repository: " + e.getMessage(), e);
        }
    }

    /**
     * Repo-wide search with glob filter.
     */
    private void executeRepoSearch(final File manifestDir) throws MojoExecutionException {
        final File repoDir = new File(localRepository);
        if (!repoDir.exists()) {
            throw new MojoExecutionException("Local repository not found: " + repoDir);
        }

        if (filter == null || filter.isEmpty()) {
            throw new MojoExecutionException("Repo-wide search requires -Dfilter=<glob>");
        }

        getLog().info("Searching " + repoDir + " with filter: " + filter);

        try {
            final List<File> jars = RepoSearch.findJarsByFilter(repoDir, filter);
            if (jars.isEmpty()) {
                getLog().warn("No jars matching filter: " + filter);
                return;
            }

            int indexed = 0;
            for (final File jar : jars) {
                final RepoSearch.ArtifactInfo info = RepoSearch.parseArtifactInfo(jar, repoDir);
                if (info == null) {
                    continue;
                }

                final File groupDir = new File(manifestDir, info.groupId());
                final File manifestFile = new File(groupDir, jar.getName() + ".manifest");

                if (manifestFile.exists() && manifestFile.lastModified() >= jar.lastModified()) {
                    continue;
                }

                writeManifest(jar, manifestFile);
                getLog().info("  " + info.groupId() + ":" + info.artifactId() + ":" + info.version());
                indexed++;
            }

            getLog().info("Indexed " + indexed + " jars from repo");
        } catch (final IOException e) {
            throw new MojoExecutionException("Error searching repository: " + e.getMessage(), e);
        }
    }

    /**
     * Search existing manifests for a class name. Returns the manifest file path if found.
     */
    private static String searchExistingManifests(final File manifestDir, final String className) {
        if (!manifestDir.exists()) {
            return null;
        }

        final File[] groupDirs = manifestDir.listFiles(File::isDirectory);
        if (groupDirs == null) {
            return null;
        }

        for (final File groupDir : groupDirs) {
            final File[] manifests = groupDir.listFiles((final File d, final String n) -> n.endsWith(".manifest"));
            if (manifests == null) {
                continue;
            }

            for (final File manifest : manifests) {
                try (final BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals(className)) {
                            return manifest.getPath();
                        }
                    }
                } catch (final IOException e) {
                    // skip
                }
            }
        }

        return null;
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

        try (final var is = getClass().getResourceAsStream("/META-INF/jackknife/USAGE.md")) {
            if (is == null) {
                getLog().warn("USAGE.md resource not found on classpath");
                return;
            }
            java.nio.file.Files.copy(is, usageFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        getLog().info("Generated " + usageFile.getPath());
    }
}
