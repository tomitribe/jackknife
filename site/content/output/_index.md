---
title: "Output"
description: "Structured JSON output — one line per call, parseable, copy-pasteable."
weight: 4
---

## Format

Every instrumented call produces one `JACKKNIFE`-prefixed JSON line:

```
JACKKNIFE {"event":"register","mode":"debug","method":"org.tomitribe.util.Join.join"}
JACKKNIFE {"event":"call","time":"12.3us","class":"Join","method":"join","args":[", ",["x","y","z"]],"return":"x, y, z"}
```

## JSON fields

| Field | Description |
|-------|------------|
| `event` | `"register"` or `"call"` |
| `time` | Elapsed time (ns, us, ms, s) |
| `class` | Simple class name |
| `method` | Method name |
| `args` | JSON array of argument values |
| `return` | Return value (absent on exception) |
| `exception` | `{"type":"...","message":"..."}` (absent on success) |
| `status` | `"returned"` or `"thrown"` (on file-reference lines) |
| `file` | Capture file path (when values too large for one line) |

## Extracting values for assertions

Strings are JSON-quoted, numbers are bare, null is `null`:

```java
// From output: "return":"a and b and c"
assertEquals("a and b and c", result);

// From output: "return":42
assertEquals(42, result);

// From output: "return":null
assertNull(result);
```

## Capture files

When values are too large for one line:

```
JACKKNIFE {"event":"call","time":"2.3ms","class":"Join","method":"join","status":"returned","file":"target/jackknife/captures/capture-0012.txt"}
```

The capture file contains the complete JSON event with all fields.

## Grepping output

```bash
Grep "JACKKNIFE" target/surefire-reports/
```
