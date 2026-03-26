---
title: "Caching"
description: "Decompile once, read forever — no Maven needed for subsequent reads."
weight: 2
---

## How caching works

- **First request** for any class in a jar decompiles the entire jar (~3-5s)
- **All subsequent reads** are direct file access — no Maven invocation

Cache location:

```
.jackknife/source/<groupId>/<artifact>-<version>/
```

## Skip Maven entirely

Before invoking `mvn jackknife:decompile`, check whether the source is
already cached:

```bash
Glob .jackknife/source/**/<ClassName>.java
```

If found, read it directly. No need to start Maven at all.

## Clearing the cache

```bash
mvn jackknife:clean -Dpath=source
```

This removes all decompiled source files. The next decompile request will
re-decompile from scratch.

## SNAPSHOT handling

The source cache does not automatically invalidate when a SNAPSHOT jar
changes. Manifests handle SNAPSHOT invalidation for the index, but if
you need fresh decompiled source for a SNAPSHOT dependency, clear the
source cache and decompile again.
