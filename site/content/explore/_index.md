---
title: "Explore"
description: "Find classes, read decompiled source, understand APIs without digging through ~/.m2."
weight: 2
---

## Manifests

After running `mvn jackknife:index`, every dependency jar has a manifest in
`.jackknife/manifest/`. Each manifest lists every class name, one per line.

Search with Grep:

```bash
Grep "CustomField" .jackknife/manifest/
```

## Finding classes in ~/.m2

If a class isn't in your project dependencies, search the local Maven repo:

```bash
mvn jackknife:index -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

Jackknife derives the search root from the package name, picks the latest
version, and indexes the jar. No `find`, no `jar tf`, no permission prompts.

For broader searches:

```bash
mvn jackknife:index -Dscope=repo -Dfilter="**jackson**"
```

## Decompiling

```bash
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

The entire jar is decompiled with Vineflower. Every class becomes a `.java`
file in `.jackknife/source/`. Subsequent reads are direct file access.
