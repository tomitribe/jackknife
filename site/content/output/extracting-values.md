---
title: "Extracting Values"
description: "Copy return values directly from JSON output into test assertions."
weight: 2
---

The primary use case: a test fails, you instrument the method, the JSON shows
you the actual value, you update the assertion. No debugger, no println, no
guessing.

## Copying values into assertions

String values — already quoted in the output, paste directly:

```java
// From output: "return":"a and b and c"
assertEquals("a and b and c", result);
```

Numeric values — bare in the output:

```java
// From output: "return":42
assertEquals(42, result);
```

Null:

```java
// From output: "return":null
assertNull(result);
```

Boolean:

```java
// From output: "return":true
assertTrue(result);
```

## Grepping for specific output

All Jackknife output:

```bash
Grep "JACKKNIFE" target/surefire-reports/
```

Failures only:

```bash
Grep '"status":"thrown"' target/surefire-reports/
```

## The workflow loop

1. Instrument the method: `mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar`
2. Run the test: `mvn test -pl my-module`
3. Read the output — the actual args and return values are right there
4. Fix the assertion
5. Clean up: `mvn jackknife:clean`
