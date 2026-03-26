---
title: "Dependencies"
description: "Instrument methods in compile, runtime, provided, and test-scoped dependencies."
weight: 1
---

## Supported scopes

All dependency scopes are supported: **compile**, **runtime**, **provided**,
and **test**. All scopes are on the test classpath, so instrumented methods
produce output during `mvn test`.

## Command

```bash
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod=join
```

## What happens under the hood

1. Searches manifests for the class
2. Writes instrumentation config to `.jackknife/instrument/<groupId>/<jar>.properties`
3. Next `mvn test` triggers the `process` goal in the **initialize** phase
4. ProcessMojo reads the config and applies the HandlerEnhancer bytecode transformation
5. Patched jar written to `.jackknife/modified/` and swapped into the classpath via `Artifact.setFile()`
6. The original jar in `~/.m2` is **never touched**

## Removal

To remove all instrumented jars and reset to the originals:

```bash
mvn jackknife:clean -Dpath=modified
```

The next `mvn test` will use the unmodified jars from `~/.m2`.
