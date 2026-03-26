---
title: "JSON Format"
description: "Every call is one line of parseable JSON with args, return values, and timing."
weight: 1
---

Every instrumented call produces one `JACKKNIFE`-prefixed JSON line on stdout.
Strings are quoted, numbers are bare, values are directly usable in assertions.
One line per call — grep and parse without multiline handling.

## Registration events

When the handler auto-discovers instrumentation config at runtime, it prints
a register event for each method:

```
JACKKNIFE {"event":"register","mode":"debug","method":"org.tomitribe.util.Join.join"}
```

This confirms instrumentation is active. If you see register events but no
call events, the method was never invoked by your test.

## Call events

A successful call:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Join","method":"join","args":[", ",["x","y","z"]],"return":"x, y, z"}
```

A call that throws:

```
JACKKNIFE {"event":"call","time":"0.1ms","class":"Join","method":"join","args":["bad"],"exception":{"type":"java.lang.IllegalArgumentException","message":"bad input"}}
```

## JSON fields

| Field | Description |
|-------|------------|
| `event` | `"register"` or `"call"` |
| `time` | Elapsed time (ns, us, ms, s) |
| `class` | Simple class name (not fully qualified) |
| `method` | Method name (`jackknife$` prefix stripped) |
| `args` | JSON array of argument values |
| `return` | Return value (absent on exception) |
| `exception` | `{"type":"...","message":"..."}` (absent on success) |
| `status` | `"returned"` or `"thrown"` (only on file-reference lines) |
| `file` | Capture file path (only when values too large for one line) |

## Value serialization

- Strings are JSON-quoted: `"hello"`
- Numbers are bare: `42`, `3.14`
- Booleans are bare: `true`, `false`
- Null is `null`
- `byte[]` shows length: `"byte[1024]"`
- `Object[]` becomes a JSON array, elements serialized recursively
- Primitive arrays use `Arrays.toString()`: `[1, 2, 3]`
- Everything else uses `toString()` as a quoted string

## Grepping output

The `JACKKNIFE` prefix makes output trivially greppable out of Maven noise:

```bash
Grep "JACKKNIFE" target/surefire-reports/
```
