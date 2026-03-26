---
title: "Goals"
description: "All Maven plugin goals — index, decompile, instrument, enhance, process, clean."
weight: 5
---

## index

Index project dependencies. Creates lightweight manifests in `.jackknife/manifest/`.

```bash
mvn jackknife:index                                              # project deps
mvn jackknife:index -Dclass=com.example.Foo                      # search ~/.m2
mvn jackknife:index -Dscope=repo -Dfilter="**jackson**"          # broad search
```

## decompile

Decompile a class from an indexed jar.

```bash
mvn jackknife:decompile -Dclass=com.example.Foo
```

## instrument

Create instrumentation config for a method or class.

```bash
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar
mvn jackknife:instrument -Dclass="org.junit.**"
```

## process

Applies instrumentation to dependency jars. Bound to `initialize` phase — runs automatically.

## enhance

Applies instrumentation to project code in `target/classes/` and `target/test-classes/`. Bound to `process-test-classes` phase.

## clean

Remove `.jackknife/` or a sub-path.

```bash
mvn jackknife:clean                              # everything
mvn jackknife:clean -Dpath=modified              # stop instrumentation
mvn jackknife:clean -Dpath=source                # clear decompile cache
```
