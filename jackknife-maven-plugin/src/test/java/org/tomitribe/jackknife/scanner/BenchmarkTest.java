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
package org.tomitribe.jackknife.scanner;

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.junit.Test;
import org.tomitribe.jackknife.index.IndexWriter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

/**
 * Compares indexing time (ASM scan) vs full decompilation time (Vineflower)
 * on real jars from the local Maven repo.
 *
 * Run with: mvn test -pl jackknife-maven-plugin -Dtest=BenchmarkTest
 */
public class BenchmarkTest {

    private static final String[][] JARS = {
            // {label, path}
            {"byte-buddy-1.18.2 (8.8MB)", findJar("net/bytebuddy/byte-buddy/1.18.2/byte-buddy-1.18.2.jar")},
            {"hibernate-core-7.2.0 (14MB)", findJar("org/hibernate/orm/hibernate-core/7.2.0.Final/hibernate-core-7.2.0.Final.jar")},
            {"eclipselink-4.0.9 (8.8MB)", findJar("org/eclipse/persistence/eclipselink/4.0.9/eclipselink-4.0.9.jar")},
    };

    @Test
    public void compareIndexVsDecompile() throws IOException {
        System.out.println();
        System.out.printf("%-35s %8s %6s %10s %10s %8s%n",
                "JAR", "CLASSES", "SIZE", "INDEX", "DECOMPILE", "RATIO");
        System.out.println("-".repeat(85));

        for (final String[] jar : JARS) {
            final String label = jar[0];
            final String path = jar[1];

            if (path == null || !new File(path).exists()) {
                System.out.println(label + " — NOT FOUND, skipping");
                continue;
            }

            final File jarFile = new File(path);
            final String sizeMB = String.format("%.1fMB", jarFile.length() / (1024.0 * 1024.0));

            // Benchmark indexing
            final long indexStart = System.nanoTime();
            final JarScanner.Result result = JarScanner.scan(jarFile);
            final long indexTime = System.nanoTime() - indexStart;

            // Write index to temp file to include I/O cost
            final File tempIndex = File.createTempFile("jackknife-bench-", ".index");
            tempIndex.deleteOnExit();
            IndexWriter.write(result, tempIndex);
            final long indexWithWriteTime = System.nanoTime() - indexStart;

            final int classCount = result.getClasses().size();

            // Benchmark decompilation
            final AtomicInteger decompiled = new AtomicInteger(0);
            final long decompileStart = System.nanoTime();

            final IResultSaver counter = new CountingSaver(decompiled);
            final Decompiler decompiler = Decompiler.builder()
                    .inputs(jarFile)
                    .output(counter)
                    .option(IFernflowerPreferences.LOG_LEVEL, "error")
                    .option(IFernflowerPreferences.THREADS, String.valueOf(Runtime.getRuntime().availableProcessors()))
                    .option(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, true)
                    .build();

            decompiler.decompile();
            final long decompileTime = System.nanoTime() - decompileStart;

            final double ratio = (double) decompileTime / indexWithWriteTime;

            System.out.printf("%-35s %8d %6s %10s %10s %7.1fx%n",
                    label, classCount, sizeMB,
                    formatNanos(indexWithWriteTime),
                    formatNanos(decompileTime),
                    ratio);
        }

        System.out.println();
    }

    @Test
    public void compareManifestVsIndex() throws IOException {
        System.out.println();
        System.out.printf("%-35s %8s %6s %10s %10s %8s%n",
                "JAR", "CLASSES", "SIZE", "MANIFEST", "INDEX", "RATIO");
        System.out.println("-".repeat(85));

        for (final String[] jar : JARS) {
            final String label = jar[0];
            final String path = jar[1];

            if (path == null || !new File(path).exists()) {
                System.out.println(label + " — NOT FOUND, skipping");
                continue;
            }

            final File jarFile = new File(path);
            final String sizeMB = String.format("%.1fMB", jarFile.length() / (1024.0 * 1024.0));

            // Benchmark manifest (just zip directory listing)
            final long manifestStart = System.nanoTime();
            final java.util.List<String> classNames = new java.util.ArrayList<>();
            final java.util.List<String> resourceNames = new java.util.ArrayList<>();
            try (final java.util.jar.JarFile jf = new java.util.jar.JarFile(jarFile)) {
                final var entries = jf.entries();
                while (entries.hasMoreElements()) {
                    final java.util.jar.JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    final String name = entry.getName();
                    if (name.endsWith(".class") && !name.endsWith("module-info.class")
                            && !name.endsWith("package-info.class")) {
                        classNames.add(name.replace('/', '.').replace(".class", ""));
                    } else {
                        resourceNames.add(name);
                    }
                }
            }

            // Write manifest to temp file
            final File tempManifest = File.createTempFile("jackknife-manifest-", ".txt");
            tempManifest.deleteOnExit();
            try (final java.io.PrintWriter mw = new java.io.PrintWriter(new java.io.FileWriter(tempManifest))) {
                for (final String cn : classNames) {
                    mw.println(cn);
                }
                mw.println("# resources");
                for (final String rn : resourceNames) {
                    mw.println(rn);
                }
            }
            final long manifestTime = System.nanoTime() - manifestStart;

            // Benchmark full index (ASM scan + write one file per class)
            final long indexStart = System.nanoTime();
            final JarScanner.Result result = JarScanner.scan(jarFile);

            // Write one file per class to simulate per-class index files
            final File tempDir = java.nio.file.Files.createTempDirectory("jackknife-bench-idx-").toFile();
            tempDir.deleteOnExit();
            for (final ClassStructure cls : result.getClasses()) {
                final File classFile = new File(tempDir, cls.getName().replace('.', '/') + ".index");
                classFile.getParentFile().mkdirs();
                classFile.deleteOnExit();
                try (final java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(classFile))) {
                    // Write just this class's index content
                    pw.println("# " + cls.getName());
                    // Simplified — just write class name + method count to measure I/O overhead
                    IndexWriter.writeClass(pw, cls);
                }
            }
            final long indexTime = System.nanoTime() - indexStart;

            final double ratio = (double) indexTime / manifestTime;

            System.out.printf("%-35s %8d %6s %10s %10s %7.1fx%n",
                    label, classNames.size(), sizeMB,
                    formatNanos(manifestTime),
                    formatNanos(indexTime),
                    ratio);
        }

        System.out.println();
    }

    private static String formatNanos(final long nanos) {
        if (nanos < 1_000_000) {
            return String.format("%.1fus", nanos / 1_000.0);
        }
        if (nanos < 1_000_000_000) {
            return String.format("%.0fms", nanos / 1_000_000.0);
        }
        return String.format("%.1fs", nanos / 1_000_000_000.0);
    }

    private static String findJar(final String repoRelativePath) {
        final String home = System.getProperty("user.home");
        final File file = new File(home, ".m2/repository/" + repoRelativePath);
        return file.exists() ? file.getAbsolutePath() : null;
    }

    private static class CountingSaver implements IResultSaver {

        private final AtomicInteger count;

        CountingSaver(final AtomicInteger count) {
            this.count = count;
        }

        @Override
        public void saveClassEntry(final String path, final String archiveName,
                                   final String qualifiedName, final String entryName,
                                   final String content) {
            if (content != null) {
                count.incrementAndGet();
            }
        }

        @Override
        public void saveClassFile(final String path, final String qualifiedName,
                                  final String entryName, final String content, final int[] mapping) {
            if (content != null) {
                count.incrementAndGet();
            }
        }

        @Override
        public void saveFolder(final String path) {
        }

        @Override
        public void copyFile(final String source, final String path, final String entryName) {
        }

        @Override
        public void createArchive(final String path, final String archiveName, final Manifest manifest) {
        }

        @Override
        public void saveDirEntry(final String path, final String archiveName, final String entryName) {
        }

        @Override
        public void copyEntry(final String source, final String path, final String archiveName, final String entry) {
        }

        @Override
        public void closeArchive(final String path, final String archiveName) {
        }
    }
}
