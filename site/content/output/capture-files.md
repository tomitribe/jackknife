---
title: "Capture Files"
description: "When values are too large for one line, the full event goes to a capture file."
weight: 3
---

When the full JSON line exceeds the threshold (default 500 characters) or
contains newlines, the complete event is written to a capture file. The
console gets a summary line instead.

## Console summary

```
JACKKNIFE {"event":"call","time":"2.3ms","class":"Join","method":"join","status":"returned","file":"target/jackknife/captures/capture-0012.txt"}
```

The summary line includes enough to triage without opening the file:

- `time` — how long the call took
- `class` and `method` — what was called
- `status` — `"returned"` or `"thrown"`
- `exception` — exception type (only when `status` is `"thrown"`)
- `file` — path to the capture file

Scan timing and status across all calls. Open only the ones that matter.

## Capture file contents

The capture file contains the COMPLETE JSON event with all fields — full
`args` array, full `return` value or `exception`, everything that would have
been on the console line if it fit.

## File naming and location

Files are named `capture-NNNN.txt`, numbered sequentially starting from
`capture-0001.txt`. Default location is `target/jackknife/captures/`.

The file reference in the summary line is a path you can read directly:

```bash
cat target/jackknife/captures/capture-0012.txt
```
