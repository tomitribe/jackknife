---
title: "Broad Search"
description: "Index multiple jars by pattern across your entire local repository."
weight: 3
---

## When to Use

The `-Dclass=` search finds one specific class. Sometimes you want to explore an entire library family — index all Jackson jars, all Commons libraries, or everything from a particular vendor. Broad search does that.

## Usage

```bash
mvn jackknife:index -Dscope=repo -Dfilter="**jackson**"
```

```
[INFO] Searching /Users/you/.m2/repository with filter: **jackson**
[INFO]   com.fasterxml.jackson.core:jackson-core:2.15.3
[INFO]   com.fasterxml.jackson.core:jackson-databind:2.15.3
[INFO]   com.fasterxml.jackson.core:jackson-annotations:2.15.3
[INFO]   com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3
[INFO]   com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.3
[INFO] Indexed 5 jars from repo
```

Both `-Dscope=repo` and `-Dfilter=` are required. The scope tells Jackknife to search the entire local repository instead of project dependencies, and the filter narrows which jars to index.

## Glob Pattern Syntax

The filter is a glob pattern matched against each jar's relative path within `~/.m2/repository`.

| Pattern | Matches |
|---------|---------|
| `*` | One path segment (no `/` crossing) |
| `**` | Any number of path segments |

### Examples

```bash
# All Jackson jars
mvn jackknife:index -Dscope=repo -Dfilter="**jackson**"

# All Apache Commons libraries
mvn jackknife:index -Dscope=repo -Dfilter="org/apache/commons/**"

# All Swagger/OpenAPI jars
mvn jackknife:index -Dscope=repo -Dfilter="**swagger**"

# All Tomitribe jars
mvn jackknife:index -Dscope=repo -Dfilter="org/tomitribe/**"

# A specific group only
mvn jackknife:index -Dscope=repo -Dfilter="io/netty/**"
```

## Latest Version Only

Like the class search, broad search groups jars by `groupId:artifactId` and keeps only the latest version of each artifact. If you have five versions of `jackson-core` in `~/.m2`, only the newest gets indexed.

This keeps manifests clean and avoids polluting search results with outdated API surfaces.

## After Indexing

Once jars are indexed, search them the same way you search project dependencies:

```bash
# Find a class across all indexed manifests
Grep "JsonParser" .jackknife/manifest/

# Decompile a class from an indexed jar
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.core.JsonParser
```

The manifests accumulate — project dependency manifests and repo search manifests all live in `.jackknife/manifest/` and are searchable together.
