---
title: "Decompile"
description: "Read decompiled source for any dependency — cached as .java files."
weight: 3
---

One command decompiles an entire jar with Vineflower. Every class becomes a
`.java` file under `.jackknife/source/`. Cached for instant re-reads. No
extraction to `/tmp`, no `javap`.

```bash
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

The first decompile for a jar takes 3-5 seconds. After that, every class in
that jar is a file on disk — read it directly, no Maven needed.
