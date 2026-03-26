---
title: "Project Code"
description: "Instrument your own classes in src/main and src/test."
weight: 2
---

## How it works

Same command — if the class is not found in dependency manifests, Jackknife
checks your project source:

```bash
mvn jackknife:instrument -Dclass=com.example.MyService -Dmethod=process
```

## Configuration

Instrumentation config is written to:

```
.jackknife/instrument/_project/project.properties
```

## Build integration

The `enhance` goal is bound to the **process-test-classes** phase. It modifies
bytecode in `target/classes/` and `target/test-classes/` in place.

Tests run against the enhanced bytecode automatically — no extra steps.

## Supported locations

Both `src/main/java` and `src/test/java` classes are supported. Whether you
want to instrument a service class or a test helper, the same command works.
