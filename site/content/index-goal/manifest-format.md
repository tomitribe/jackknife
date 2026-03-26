---
title: "Manifest Format"
description: "What's in a manifest file and how to search them."
weight: 4
---

## File Location

Manifests live in `.jackknife/manifest/` with one file per indexed jar:

```
.jackknife/manifest/<groupId>/<artifact>-<version>.jar.manifest
```

The groupId is a flat directory name with dots — not nested directories. This keeps the structure shallow and predictable:

```
.jackknife/manifest/
  com.fasterxml.jackson.core/
    jackson-core-2.15.3.jar.manifest
    jackson-databind-2.15.3.jar.manifest
    jackson-annotations-2.15.3.jar.manifest
  org.tomitribe/
    tomitribe-util-1.3.23.jar.manifest
  jakarta.servlet/
    jakarta.servlet-api-6.0.0.jar.manifest
```

## Format

A manifest file is plain text. Class names are listed one per line in dotted format, followed by an optional resources section.

### Example

```
com.fasterxml.jackson.core.JsonFactory
com.fasterxml.jackson.core.JsonFactory$Feature
com.fasterxml.jackson.core.JsonGenerator
com.fasterxml.jackson.core.JsonGenerator$Feature
com.fasterxml.jackson.core.JsonParser
com.fasterxml.jackson.core.JsonParser$Feature
com.fasterxml.jackson.core.JsonParser$NumberType
com.fasterxml.jackson.core.JsonToken
com.fasterxml.jackson.core.ObjectCodec
com.fasterxml.jackson.core.Version
com.fasterxml.jackson.core.io.IOContext
com.fasterxml.jackson.core.json.JsonReadContext
com.fasterxml.jackson.core.json.JsonWriteContext
com.fasterxml.jackson.core.type.TypeReference
com.fasterxml.jackson.core.util.DefaultPrettyPrinter
# resources
META-INF/MANIFEST.MF
META-INF/LICENSE
META-INF/NOTICE
META-INF/services/com.fasterxml.jackson.core.JsonFactory
META-INF/versions/9/module-info.class
```

### What's Included

- Every `.class` file in the jar, converted to dotted class name format
- Inner classes appear with their `$` separator: `JsonFactory$Feature`
- Resources are listed after a `# resources` header line

### What's Excluded

- `module-info.class` — skipped, not a real class
- `package-info.class` — skipped, contains only annotations
- Directory entries — skipped, only files are listed

### What's NOT in the Manifest

No method signatures, no field info, no bytecode analysis. The manifest is purely a listing of what's in the jar. This is intentional — it's why indexing is sub-second. Method signatures and implementation details come from the decompile step, which produces full `.java` source files on demand.

## Searching Manifests

### Find a Class

```bash
Grep "ObjectMapper" .jackknife/manifest/
```

This tells you which jar contains the class and gives you the full package name. If you searched for `ObjectMapper`, you'll see it's `com.fasterxml.jackson.databind.ObjectMapper` in `jackson-databind-2.15.3.jar.manifest`.

### Find Inner Classes

```bash
Grep "Join\$" .jackknife/manifest/
```

Lists all inner classes of `Join`: `Join$NameCallback`, `Join$StringFormatCallback`, etc.

### Find Resources

```bash
Grep "META-INF/services" .jackknife/manifest/
```

Shows all jars that register service providers, and which interfaces they provide implementations for.

### Find Classes by Pattern

```bash
Grep "Factory$" .jackknife/manifest/
```

All classes ending in `Factory` across every indexed jar.
