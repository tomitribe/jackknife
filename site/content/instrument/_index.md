---
title: "Instrument"
description: "Add debug output to any method — dependency or project code — without changing source."
weight: 4
---

Instrument a method to see args, return values, exceptions, and timing as
structured JSON. No source changes, no dirty diffs. Two-step workflow:

```bash
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar
mvn test
```

Debug output appears as JSON during your test run:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Foo","method":"bar","args":["hello"],"return":"world"}
```
