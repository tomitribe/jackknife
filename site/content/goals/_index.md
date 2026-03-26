---
title: "Goals"
description: "All Maven plugin goals — index, decompile, instrument, process, enhance, clean."
weight: 6
---

## index

Build lightweight manifests for dependency jars. Creates one manifest per jar
in `.jackknife/manifest/` — just class names and resource paths, no ASM, no
decompilation. Sub-second for an entire classpath.

### Parameters

| Parameter | Description |
|-----------|------------|
| `-Dclass=<className>` | Search `~/.m2/repository` for a specific class |
| `-Dscope=repo` | Widen search to entire local repository |
| `-Dfilter=<glob>` | Filter for repo-wide search (requires `-Dscope=repo`) |

### Details

- Resolution scope: `TEST` — sees compile, runtime, provided, and test dependencies
- Can be bound to the `initialize` phase for automatic indexing
- SNAPSHOT jars are re-indexed when the jar is newer than the manifest
- Generates `.jackknife/USAGE.md` with full instructions on each run

### Examples

```bash
# Index project dependencies
mvn jackknife:index

# Find a class in ~/.m2/repository
mvn jackknife:index -Dclass=com.fasterxml.jackson.databind.ObjectMapper

# Broad search across all cached jars
mvn jackknife:index -Dscope=repo -Dfilter="**jackson**"
```

---

## decompile

Decompile a class from an indexed jar using Vineflower. Decompiles the entire
jar on first request — every class becomes a `.java` file in `.jackknife/source/`.
Subsequent reads are direct file access.

### Parameters

| Parameter | Description |
|-----------|------------|
| `-Dclass=<className>` | Fully qualified class name (required) |

### Details

- Aggregator: yes — runs once at the reactor root
- Resolution scope: `TEST`
- Searches manifests first, falls back to project artifacts
- Output goes to `.jackknife/source/<groupId>/<artifact>/`

### Examples

```bash
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.databind.ObjectMapper
mvn jackknife:decompile -Dclass=org.junit.Assert
```

---

## instrument

Create instrumentation config for methods or classes. Searches manifests
to find which jar contains the target, then writes config files to
`.jackknife/instrument/`.

### Parameters

| Parameter | Description |
|-----------|------------|
| `-Dclass=<pattern>` | Class name or glob pattern (required) |
| `-Dmethod=<name>` | Method name, optionally with parameter types |
| `-Dmode=debug\|timing` | Instrumentation mode (default: `debug`) |

### Details

- Aggregator: yes — runs once at the reactor root
- If no `-Dmethod`, instruments all methods of the class
- Glob patterns: `*` matches one package level, `**` matches any depth
- Also checks `src/main/java/` and `src/test/java/` for project classes

### Examples

```bash
# One specific method
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod=join

# One method with exact signature
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod="join(String,Collection)"

# All overloads of a method
mvn jackknife:instrument -Dclass=org.junit.Assert -Dmethod=assertEquals

# All methods of a class
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join

# Wildcard — all classes matching a pattern
mvn jackknife:instrument -Dclass="org.junit.**" -Dmethod=assertEquals

# All Handler classes
mvn jackknife:instrument -Dclass="**Handler"

# Timing mode — elapsed time and status only, no args or return values
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar -Dmode=timing
```

---

## process

Apply instrumentation to dependency jars. Reads config from
`.jackknife/instrument/`, transforms matching classes with bytecode
enhancement, writes patched jars to `.jackknife/modified/`, and swaps
them into the Maven classpath via `Artifact.setFile()`.

### Details

- Lifecycle phase: `initialize` — runs before compilation
- Non-aggregator: runs per-module
- Resolution scope: `TEST`
- Automatic — no user invocation needed when bound in `pom.xml`

---

## enhance

Apply instrumentation to project code. Reads config from
`.jackknife/instrument/_project/` and modifies compiled `.class` files
in `target/classes/` and `target/test-classes/` in place. Writes
`META-INF/jackknife/handlers.properties` for runtime auto-discovery.

### Details

- Lifecycle phase: `process-test-classes` — runs after test compilation
- Modifies `target/classes/` and `target/test-classes/` directly
- Automatic — no user invocation needed when bound in `pom.xml`

---

## clean

Remove `.jackknife/` or a sub-path within it.

### Parameters

| Parameter | Description |
|-----------|------------|
| `-Dpath=<relative-path>` | Sub-path within `.jackknife/` (optional) |

### Details

- Path validation: rejects `..`, absolute paths, and `:` characters
- Resolved path is verified to stay within `.jackknife/`

### Examples

```bash
# Remove everything
mvn jackknife:clean

# Stop instrumentation (remove modified jars)
mvn jackknife:clean -Dpath=modified

# Clear decompile cache
mvn jackknife:clean -Dpath=source

# Remove a specific groupId's modifications
mvn jackknife:clean -Dpath=modified/org.tomitribe
```
