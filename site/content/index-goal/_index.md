---
title: "Index"
description: "Find any class in any jar — project dependencies or ~/.m2."
weight: 2
---

The `index` goal builds lightweight manifests for dependency jars. Each manifest lists every class name and resource path — enough to answer "which jar has this class?" in milliseconds.

## Two-Tier Architecture

Jackknife uses a deliberate two-tier design:

1. **Manifest** (sub-second, all jars) — reads the zip directory only. No decompression, no bytecode parsing. This is why `mvn jackknife:index` is instant even on large classpaths.

2. **Full decompile** (3-5s per jar, on demand) — Vineflower decompiles the entire jar into individual `.java` files. One-time cost per jar, then cached as direct file reads.

The manifest answers "where is this class?" The decompile step answers "what does it do?"

## Quick Start

```bash
mvn jackknife:index
```

This creates `.jackknife/manifest/` with one manifest file per dependency jar.

Search with Grep:

```bash
Grep "ObjectMapper" .jackknife/manifest/
```
