---
title: "Project Dependencies"
description: "Index all dependencies in your Maven project."
weight: 1
---

The default behavior of `mvn jackknife:index` indexes every resolved dependency in your Maven project. No flags, no configuration — just run it.

## What Gets Indexed

Jackknife resolves dependencies using `ResolutionScope.TEST`, which means all scopes are visible: compile, runtime, provided, and test. If Maven can see it, Jackknife will manifest it.

```bash
mvn jackknife:index
```

```
[INFO] Manifested 47 jars, skipped 0 (up to date or non-jar)
[INFO] Generated .jackknife/USAGE.md
```

## Where Manifests Go

Manifests are written to `.jackknife/manifest/` at the reactor root (the directory where you ran `mvn`). In a multi-module project, every module's dependencies are indexed into the same shared manifest directory.

The path structure is:

```
.jackknife/manifest/<groupId>/<artifact>-<version>.jar.manifest
```

For example:

```
.jackknife/manifest/com.fasterxml.jackson.core/jackson-databind-2.15.3.jar.manifest
```

## Deduplication

Shared dependencies are indexed once. If `module-a` and `module-b` both depend on `jackson-databind-2.15.3.jar`, the manifest is written during whichever module runs first. The second module sees the existing manifest file and skips it.

This deduplication uses timestamp comparison: if the manifest file is newer than (or the same age as) the jar, it's up to date.

## SNAPSHOT Invalidation

Released jars never change, so their manifests are written once and never touched again. SNAPSHOT jars are different — they can be rebuilt at any time.

Jackknife handles this by comparing timestamps. If the jar file is newer than its manifest, the manifest is regenerated. If the manifest is already up to date, it's skipped. This means re-running `mvn jackknife:index` after a SNAPSHOT rebuild re-indexes only what changed.

## Generated USAGE.md

Every project index run generates `.jackknife/USAGE.md` with full instructions for searching manifests, decompiling classes, and using the instrument goal. This file is the reference card — read it once, then search it when you forget a flag.

## Multi-Module Projects

In a multi-module build, each module contributes its dependencies to the shared `.jackknife/manifest/` directory at the reactor root. The manifest directory accumulates across modules, so after a full build you have coverage of the entire project's dependency tree.

```bash
# From the reactor root
mvn jackknife:index

# Search across all modules' dependencies
Grep "HttpServletRequest" .jackknife/manifest/
```
