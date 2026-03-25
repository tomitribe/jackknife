# Jackknife — Design Document

## Goal
A Maven plugin that Claude Code can use during coding sessions to inspect, decompile, and instrument classes in Java jar dependencies. One `mvn` command bootstraps everything; after that, Claude reads files directly with zero Maven overhead.

## Stack
| Layer | Library | Role |
|-------|---------|------|
| Plugin | Maven Plugin API | Plugin goals: index, decompile, instrument, process |
| Decompile | Vineflower | Full source decompilation of jar classes |
| Transform | ASM (HandlerEnhancer) | Bytecode rename + InvocationHandler wrapper |
| Runtime | jackknife-runtime | InvocationHandler impls (DebugHandler, TimingHandler, ProceedHandler) |

**Not used (evaluated and dropped):**
- Snitch — too coupled to its Tracker infrastructure; wrote HandlerEnhancer fresh instead
- Archie — not needed; simple jar copy loop with selective class transformation is sufficient
- Crest — plugin-only, no CLI
- xbean-finder — reference for ASM visitor pattern, not a dependency
- ASM structural index — benchmarked at 37-117x slower than manifest, still needed decompilation; no value in the middle tier

## Architecture: Two-Tier

Benchmarking drove the architecture. Three tiers were evaluated:

| Tier | Method | Speed (8MB jar) | What you get |
|------|--------|-----------------|-------------|
| **Manifest** | Zip directory listing | 4-16ms | Class names + resource paths |
| ASM Index | Full bytecode scan | 200-800ms | Structural metadata (methods, fields, annotations, exceptions) |
| **Decompile** | Vineflower | 3-5s | Full Java source |

The ASM index was 37-117x slower than manifest but still required decompilation for actual method logic. No value in the middle tier.

**Result: manifest (all jars, sub-second) + decompile (per jar, on demand).**

After `mvn jackknife:index`, Claude greps manifests and reads decompiled source files directly. No Maven invocations for read-only operations.

## Plugin Goals

| Goal | Invoked by | Purpose |
|------|-----------|---------|
| `index` | User/Claude | Build manifests for all dependency jars + generate USAGE.md |
| `decompile` | User/Claude | Decompile entire jar on first request; subsequent reads are file access |
| `instrument` | User/Claude | Write instrumentation config to `.jackknife/instrument/` |
| `process` | Lifecycle (automatic) | Apply bytecode transforms, swap patched jars via `Artifact.setFile()` |

**Dropped goals:** `find` and `describe` — Claude greps manifests directly, faster than Maven invocations.

## Directory Structure

```
.jackknife/
├── manifest/              Class listings (all jars, sub-second)
│   └── <groupId>/
│       └── <artifact>-<version>.jar.manifest
├── source/                Decompiled source (per jar, on demand)
│   └── <groupId>/
│       └── <artifact>-<version>/
│           └── com/example/MyClass.java
├── instrument/            Instrumentation inbox
│   └── <groupId>/
│       └── <artifact>-<version>.jar.properties
├── modified/              Patched jars + receipts
│   └── <groupId>/
│       ├── <artifact>-<version>.jar
│       └── <artifact>-<version>.jar.properties
└── USAGE.md               Auto-generated reference (do not edit)
```

**Path conventions:**
- groupId is flat (`org.apache.httpcomponents/` not `org/apache/httpcomponents/`)
- Filenames mirror the artifact: `<artifactId>-<version>.<packaging>.<our-extension>`

**Filesystem is the state machine:**
- File in `instrument/` → needs work
- File in `modified/` → done, actively swapped in
- File deleted → reverted
- `rm -rf .jackknife/` → fully clean, plugin profile deactivates

## Instrumentation Architecture

### InvocationHandler chain (delegate pattern)

HandlerEnhancer renames target methods (`process` → `jackknife$process`) and generates a wrapper that delegates to an `InvocationHandler`. Handlers wrap handlers:

```
TimingHandler → DebugHandler → ProceedHandler
```

- **TimingHandler** — measures elapsed time (lightweight, no value capture)
- **DebugHandler** — logs ENTER (args), EXIT (return value), THROW (exception). Large values written to `target/jackknife/captures/` with file reference.
- **ProceedHandler** — calls the renamed original method via reflection. No bytecode generation needed.

New capabilities = new InvocationHandler. No ASM work.

### HandlerRegistry

Auto-discovers handler configs from `META-INF/jackknife/handlers.properties` injected into patched jars. On first `getHandler()` call, scans classpath and builds handler chains. Falls back to direct method call if no handler registered.

### Two modes

- `debug` — args + return + exceptions. For behavior analysis.
- `timing` — elapsed time only. For performance work (value capture would skew measurements).

### Large value capture

Threshold-based: if total line length exceeds threshold or value contains newlines, write to `target/jackknife/captures/ClassName-method-NNN.txt` and reference in console. Claude reads capture files directly for test assertions.

## Decisions Made
| # | Decision | Rationale | Alternatives Considered |
|---|----------|-----------|------------------------|
| 1 | Two-tier architecture: manifest + on-demand decompile | Benchmark showed ASM index was 37-117x slower than manifest with no value over decompilation. Manifest is sub-second for all jars. Decompile is 3-5s per jar, done once. | Three tiers with ASM index (middle tier adds cost without eliminating next step) |
| 2 | Decompile entire jar on first class request | Already reading the jar; decompile everything. Subsequent reads for any class in that jar are file reads with no Maven. | Single-class decompile (repeated Maven invocations for each class) |
| 3 | Per-class source files, not per-jar | Claude uses Read tool with offset/limit on individual files. One large file requires grep+context. | Single file per jar (hard to navigate, can't read specific classes) |
| 4 | Artifact.setFile() for deployment | Original jar never touched. Instrumentation exists only during builds with plugin configured. Clean revert by removing config. | Classifier strategy (excludes cascade), in-place overwrite (backup/restore) |
| 5 | InvocationHandler delegation, not hardcoded bytecode | All behavior in plain Java handler code. New capabilities = new handler class. No ASM work per feature. | Hardcoded timing/tracking bytecode (new ASM work for every feature) |
| 6 | ProceedHandler via reflection, not bytecode generation | Method.invoke() handles all method shapes (void, primitives, arrays, static, varargs) via reflection boxing. No Airlift Bytecode or raw ASM needed. | Generated ProceedHandler (requires bytecode generation for every method shape) |
| 7 | Write HandlerEnhancer fresh, not depend on Snitch | Snitch is too coupled to its Tracker infrastructure. Our wrapper is simpler (InvocationHandler delegation). Avoids Snitch compilation issues (sun.misc.Unsafe on modern JDK). | Fix Snitch and depend on it (fixing+overriding half the code) |
| 8 | Plugin-only, no CLI | One moving part. No version sync. Resolved classpath for free. After index, reads are file access not Maven. | Plugin + CLI (two artifacts to maintain and version-sync) |
| 9 | Plugin self-generates USAGE.md | Docs and tool can't drift. CLAUDE.md just says "run index, read USAGE.md." 3 lines, never needs updating. | Hand-maintained skill file (drifts), detailed CLAUDE.md entry (maintenance) |
| 10 | Timestamp-based cache invalidation, no force flag | Force flag is a code smell — complexity pushed to user. Timestamp comparison handles SNAPSHOTs. Released artifacts never change. | --force flag (band-aid for bugs) |
| 11 | Exact entry name matching in decompiler saver | `startsWith` matched `CustomFieldDisplayType` when `CustomField` was requested. `equals` on entry name prevents wrong-class capture. | Prefix matching (captures wrong classes with similar names) |
| 12 | Instrument project code too, not just dependencies | Same InvocationHandler applied to `target/classes/` after compile. Better than println: no source modification, no dirty diffs, no forgotten debug code. | Print statements (modify source, forget to remove) |
| 13 | Project configs stay in instrument/ (not moved) | Project instrumentation targets `target/classes/` which is ephemeral — config must persist for re-application each compile. Dependency configs move to modified/ as receipt. | Separate directories (unnecessary), always move (breaks project re-application) |
| 14 | Plugin declared in parent pom profile, auto-activated by `.jackknife/` directory | Filesystem-as-state-machine. `rm -rf .jackknife/` deactivates completely. Parent pom is set-and-forget. | Per-project pom (tedious), settings.xml only (can't bind to lifecycle) |
| 15 | Flat groupId in directory paths | Avoids ambiguity of undetermined directory depth. Maven only went to dots-as-dirs for scale we don't have. | Exploded groupId dirs (ambiguous depth) |
| 16 | Two artifacts: plugin + runtime | Plugin has heavy deps (Vineflower, ASM) isolated by Maven classloader. Runtime is small, added to project classpath. | Single artifact (classloader conflicts) |
| 17 | Forgiving method parser in instrument goal | Accepts index format, Snitch format, method-name-only. Strips modifiers, param names, return types. Eliminates format errors. | Strict format only (error-prone copy-paste) |
| 18 | Method-name-only instrumentation targets all matching methods | "Instrument `retryRequest` everywhere" — cross-cutting. Can narrow with `-Dimplements=` or `-Dpackage=`. | Require exact FQN always (tedious) |
| 19 | Dropped find/describe goals | Claude greps manifest files directly — faster than Maven invocation. Goals added no value over file access. | Keep goals (unnecessary Maven overhead) |
| 20 | Severed tomitribe-parent from oss-parent | `com.tomitribe` namespace not verified on Central portal. Inherited Central publishing config fought private Nexus deployment. Two failed releases. The inheritance provided nothing the private parent didn't already override. | Keep inheritance (broken publishing) |

## Rejected Alternatives
| Alternative | Why Rejected |
|-------------|-------------|
| ASM structural index (middle tier) | 37-117x slower than manifest, still needed decompilation for actual code. No value. |
| Snitch as dependency | Too coupled to Tracker. Compilation issues on modern JDK. Would throw away most code. |
| Archie for jar repackaging | Simple jar copy loop sufficient. Archie adds dependency without enough benefit. |
| CLI tool (Crest-based) | Plugin-only is simpler. After index, reads are file access. CLI extraction deferred. |
| Single-class decompile | Repeated Maven invocations. Whole-jar decompile pays once, then file reads. |
| `--force` flag for cache | Code smell. Timestamp comparison handles invalidation. |
| `startsWith` class matching | Captures wrong classes with similar names (CustomField vs CustomFieldDisplayType). |
| find/describe plugin goals | Claude greps files directly, faster than Maven goals. |
| Classifier strategy for deps | Cascading excludes for transitive deps eat more time than debugging saves. |
| In-place jar overwrite | Requires backup/restore. Artifact.setFile() is cleaner. |
| Shared knowledge file across sessions | Collision risk. Claude memory system handles this better. |
| Relative path CLI approval | Claude Code permission system uses literal string matching, breaks across directory changes. |
| Central publishing for com.tomitribe | Namespace not verified. Private parent doesn't belong on Central. |

## Parent Pom Setup
- `org.tomitribe:oss-parent:14+` — jackknife profile included (public/Central projects)
- `com.tomitribe:tomitribe-parent:14+` — jackknife profile included, **severed from oss-parent** (private/Nexus projects)
- Both auto-activate on `.jackknife/` directory existence

## Open / Future Work
- Index format: one large manifest file per jar vs one file per class (benchmark showed filesystem overhead on many small files — currently one file per jar for manifests, one file per class for decompiled source)
- Uber jar awareness: skip project's own output artifact to avoid double-indexing
- Runtime jar shading: tomitribe-util relocated like Churchkey to avoid classpath conflicts
- Multi-module `.jackknife/` location: currently uses `${session.executionRootDirectory}`
