---
title: "Matching"
description: "From one method to an entire package — control what gets instrumented."
weight: 3
---

## Granularity

| Input | What gets instrumented |
|-------|----------------------|
| `-Dclass=Foo -Dmethod="bar(String,int)"` | That one method |
| `-Dclass=Foo -Dmethod=bar` | All overloads of bar |
| `-Dclass=Foo` | All methods of Foo |
| `-Dclass="org.junit.**" -Dmethod=assertEquals` | assertEquals in all junit classes |
| `-Dclass="**Handler"` | All methods of any class ending in Handler |

## Glob patterns

The `-Dclass` parameter supports glob patterns:

- `*` matches one package level (e.g., `org.example.*` matches classes directly in `org.example`)
- `**` matches any depth (e.g., `org.example.**` matches classes in `org.example` and all sub-packages)

## Optional method

The `-Dmethod` parameter is optional. Omit it to instrument all methods of
the matched class(es).

## Required class

The `-Dclass` parameter is required. This prevents accidental global
instrumentation — you must always specify which classes to target.
