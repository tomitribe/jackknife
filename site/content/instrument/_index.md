---
title: "Instrument"
description: "Add debug output to any method — dependency or project code — without changing source."
weight: 3
---

## Basic usage

```bash
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod=join
mvn test
```

## Matching granularity

| Input | What gets instrumented |
|-------|----------------------|
| `-Dclass=Foo -Dmethod="bar(String,int)"` | That one method |
| `-Dclass=Foo -Dmethod=bar` | All overloads of bar |
| `-Dclass=Foo` | All methods of Foo |
| `-Dclass="org.junit.**" -Dmethod=assertEquals` | assertEquals in all junit classes |
| `-Dclass="**Handler"` | All methods of any class ending in Handler |

## Modes

- **debug** (default) — args, return values, exceptions, and timing
- **timing** — elapsed time and status only

```bash
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar -Dmode=timing
```

## Project code

Jackknife can instrument your own classes too. If the class isn't found in
dependency manifests, it checks `src/main/java/` and `src/test/java/`:

```bash
mvn jackknife:instrument -Dclass=com.example.MyService -Dmethod=process
```

The `enhance` goal modifies `target/classes/` and `target/test-classes/`
in place during the build. Tests run against the enhanced bytecode.
