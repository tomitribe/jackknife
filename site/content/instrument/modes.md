---
title: "Modes"
description: "Debug mode shows everything. Timing mode shows just elapsed time."
weight: 4
---

## Available modes

### debug (default)

Args, return values, exceptions, and timing — all in one JSON line:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Foo","method":"bar","args":["hello"],"return":"world"}
```

### timing

Elapsed time and status only. No args or return values:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Foo","method":"bar","status":"returned"}
```

## Selecting a mode

```bash
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar -Dmode=timing
```

## When to use timing

- **Performance profiling** — find slow methods without the noise of arg/return data
- **Finding bottlenecks** — narrow down which method in a call chain is expensive
- **Lightweight observation** — when you know what the method does and just need to know how long it takes
