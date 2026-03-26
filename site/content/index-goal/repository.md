---
title: "Repository Search"
description: "Find any class in ~/.m2/repository by name — no shell commands needed."
weight: 2
---

## The Problem

Finding a class in `~/.m2/repository` is painful. Developers end up chaining `find`, `jar tf`, and `javap` — each requiring its own permission prompt in Claude, each producing output that needs manual parsing. Even when you know the fully qualified class name, locating the right jar version in a repository with thousands of artifacts is tedious.

## The Solution

```bash
mvn jackknife:index -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

One command. Jackknife finds the jar, indexes it, and tells you exactly where the class lives.

```
[INFO] Searching /Users/you/.m2/repository for com.fasterxml.jackson.databind.ObjectMapper...
[INFO] Found com.fasterxml.jackson.databind.ObjectMapper in com.fasterxml.jackson.core:jackson-databind:2.15.3
[INFO] Indexed jackson-databind-2.15.3.jar → .jackknife/manifest/com.fasterxml.jackson.core/jackson-databind-2.15.3.jar.manifest
```

## How the Algorithm Works

The search is designed to be fast even on large local repositories. Here's the full sequence:

### 1. Check Existing Manifests

Before touching `~/.m2`, Jackknife scans `.jackknife/manifest/` for the class name. If you've already indexed a jar containing this class (from a previous project index or repository search), the answer comes back instantly. No filesystem walking at all.

### 2. Derive Search Root from Package

This is the key optimization. The class package name tells Jackknife where to start looking:

```
com.fasterxml.jackson.databind.ObjectMapper
     └─────────┘
     first 2 segments
```

Jackknife takes the first two segments of the package — `com.fasterxml` — and maps them to the repository path `~/.m2/repository/com/fasterxml/`. The search starts there, not at the repository root.

This eliminates 90%+ of `~/.m2`. When looking for a `com.fasterxml` class, Jackknife never walks `org/apache/`, `io/netty/`, or any other top-level directory.

### 3. Collect All Jars Under the Search Root

Jackknife walks the search root directory, collecting every `.jar` file. Source jars (`-sources.jar`), javadoc jars (`-javadoc.jar`), and test jars (`-tests.jar`) are filtered out.

### 4. Group by Artifact, Keep Latest Version

Multiple versions of the same artifact often exist in `~/.m2`. Jackknife groups jars by `groupId:artifactId` and keeps only the latest version of each.

Version comparison uses numeric component parsing, not string comparison. This means `2.14.0` correctly sorts higher than `2.9.1` — splitting on non-numeric characters and comparing each integer segment.

```
2.9.1  → [2, 9, 1]
2.14.0 → [2, 14, 0]   ← latest: 14 > 9
2.15.3 → [2, 15, 3]   ← latest: 15 > 14
```

### 5. Sort by Affinity

Not all artifacts under `com/fasterxml/` are equally likely to contain `com.fasterxml.jackson.databind.ObjectMapper`. Jackknife scores each jar by how much of its `groupId.artifactId` path prefix-matches the target class's package name.

For example, searching for `com.fasterxml.jackson.databind.ObjectMapper`:

| Artifact | Affinity Path | Match Length |
|----------|--------------|-------------|
| `com.fasterxml.jackson.core:jackson-databind` | `com.fasterxml.jackson.core.jackson-databind` | 30 chars |
| `com.fasterxml.jackson.core:jackson-core` | `com.fasterxml.jackson.core.jackson-core` | 26 chars |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `com.fasterxml.jackson.datatype.jackson-datatype-jsr310` | 25 chars |

Higher affinity jars are checked first. In practice, this means the correct jar is usually the first one checked.

### 6. Check the Jar's Zip Directory

For each jar (in affinity order), Jackknife opens the zip directory and looks for the `.class` entry. This is a direct lookup — no decompression, no scanning. If the entry exists, this jar is the answer.

### 7. Index and Done

The first jar that contains the class is indexed (a manifest is written to `.jackknife/manifest/`), and the search stops. The jar is now available for decompiling with `mvn jackknife:decompile -Dclass=...`.

## Why It's Fast

The search root derivation is the critical insight. A typical `~/.m2/repository` might contain 50,000+ jars across hundreds of top-level directories. By narrowing to the first two package segments, the search space drops to a few hundred jars at most. Combined with latest-version filtering and affinity sorting, the actual number of jars opened is usually one or two.
