---
title: "Basic Usage"
description: "Decompile a class from any indexed jar."
weight: 1
---

## Prerequisite

The jar containing the class must be indexed first:

```bash
mvn jackknife:index
```

This creates manifests in `.jackknife/manifest/` for every dependency jar.
The decompile goal searches these manifests to locate the class.

## Decompiling a class

```bash
mvn jackknife:decompile -Dclass=com.example.MyClass
```

What happens:

1. Searches manifests for the fully-qualified class name
2. Locates the jar in your local Maven repository
3. Decompiles the **entire jar** with Vineflower
4. Prints the requested class source to stdout
5. Writes all decompiled `.java` files to `.jackknife/source/`

## Why the entire jar?

Decompiling one class or an entire jar takes roughly the same time (~3-5s).
By decompiling the whole jar up front, every subsequent class read from that
jar is a direct file access — no Maven invocation, no decompiler startup.

## Error handling

If the class is not found in any manifest, the error message includes exact
recovery commands — check your spelling, run `jackknife:index`, or search
the local repo.

## Multi-module projects

The decompile goal searches manifests at the reactor root, so it works from
any module in a multi-module project. Jars are resolved from the local
Maven repository.
