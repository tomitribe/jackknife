# Jackknife — Initial Design

## Goal
A Maven plugin (and potentially CLI) that Claude can invoke during coding sessions to list, decompile, and instrument classes in Java jars. The tool combines Vineflower (decompilation), Snitch (bytecode instrumentation), and Archie (jar repackaging) behind Maven plugin goals. It should be globally approved so Claude can use it without prompting.

## Stack
| Layer | Library | Role |
|-------|---------|------|
| CLI / Plugin | Crest / Maven Plugin API | Command structure |
| Resolve | tomitribe-util (Mvn) | GAV coordinates → jar file path in local repo |
| Dir structure | tomitribe-util (Dir) | Strongly typed filesystem access to `.jackknife/` |
| Index | ASM (full read, no SKIP_CODE) | Single-pass structural metadata extraction + thrown exceptions |
| Reference | xbean-finder (AnnotationFinder) | Proven ASM visitor pattern for classpath-scale scanning |
| Understand | Vineflower | Decompile classes to readable Java |
| Instrument | Snitch | ASM-based method wrapping, timing, listeners |
| Repackage | Archie | Stream jar, apply transforms, write patched jar |
| Style | tio | Reference for command patterns, colors |

## Capabilities
1. **Resolution** — Go from a Maven coordinate (`g:a:v`) or a class name to the actual jar file on disk. Powered by `Mvn` class which walks up from its own jar to find the repo root.
2. **Discovery** — What's in this jar? Classes, packages, resources (including `META-INF/services`, config files, etc.).
3. **Understanding** — Decompile specific classes to readable Java source.
4. **Observation** — Instrument methods to see runtime values, timing, call flow.
5. **Search** — Which jar contains a given class? Search across jars in the local repo or on a project's classpath.
6. **Packaging** — Create a modified jar with instrumentation baked in. Primary strategy: `Artifact.setFile()` in plugin swaps dependency in-flight during builds. Original jar never touched.
7. **Reversal** — Get back to clean. Delete from `modified/`, next build uses original jars.

## Directory Structure

```
.jackknife/                                         ← project root, gitignored
├── instrument/                                  ← "inbox" — Claude drops config here
│   └── org.apache.httpcomponents/
│       └── httpclient-4.5.13.jar.properties     ← Snitch properties format
├── modified/                                    ← "done" — plugin moves config here + patched jar
│   └── org.apache.httpcomponents/
│       ├── httpclient-4.5.13.jar                ← patched jar
│       └── httpclient-4.5.13.jar.properties     ← receipt (moved from instrument/)
└── index/
    └── org.apache.httpcomponents/
        └── httpclient-4.5.13.jar.index          ← structural metadata, greppable
```

**Path conventions:**
- groupId is flat (`org.apache.httpcomponents/` not `org/apache/httpcomponents/`) — avoids ambiguity of undetermined directory depth
- Filenames mirror the artifact: `<artifactId>-<version>.<packaging>.<our-extension>` — no ambiguity with classifiers or packaging types
- Flat within each groupId — simpler listing, glob cleanup (`httpclient-4.5.13.jar*` gets jar + properties)
- Dir proxy (tomitribe-util) provides strongly typed access to this structure

**Plugin behavior (automatic on every build):**
1. **Process** — scan `instrument/`, apply Snitch+Archie, write patched jar to `modified/`, move properties file to `modified/`
2. **Swap** — scan `modified/`, `Artifact.setFile()` for every patched jar found

**Filesystem is the state machine:**
- File in `instrument/` → needs work
- File in `modified/` → done, actively swapped in
- File deleted from `modified/` → reverted

## Decisions Made
| # | Decision | Rationale | Alternatives Considered |
|---|----------|-----------|------------------------|
| 1 | Artifact.setFile() for deployment — no jar overwriting, no classifier, no pom dep changes | Original jar never touched. Instrumentation exists only during builds with plugin configured. Clean revert by removing config. Doesn't need IDE support (debugger is the Ferrari). | Classifier strategy (excludes cascade), in-place overwrite (backup/restore complexity) |
| 2 | Instrumentation config via Snitch properties files in `.jackknife/instrument/` | Plugin is always declared but no-ops without config files. Snitch format is proven and simple. Filesystem-as-state-machine: instrument/ is inbox, modified/ is done pile. | System properties (ugly), pom modifications (heavier), target/ file (lost on clean), single config file at root (doesn't encode GAV) |
| 3 | Start plugin-only, extract CLI later if Maven startup overhead is a real problem | One moving part. No version sync issues. Resolved classpath for free. Index format is flat text — splitting later costs nothing. "Build the music, name the band later." | Build both upfront (over-engineering distribution before validating the tool itself) |
| 4 | Rich structural index, not just class names | Jar reading is the expensive part (compressed sequential blob). Once you crack it open, extract everything useful in one pass. Most lookups answered from index without decompiling. | Flat class list (too sparse — still need to open jar for basic questions) |
| 5 | Full ASM read — no SKIP_CODE, no SKIP_DEBUG | Need exceptions thrown inside method bodies (not just declared throws clause). Also preserves parameter names from LocalVariableTable/MethodParameters. Scale back only if perf is a real problem. xbean-finder proves the one-pass ASM visitor pattern works at classpath scale. | SKIP_CODE (misses thrown exceptions from method bodies), SKIP_CODE + SKIP_DEBUG (also loses parameter names) |
| 6 | Capture both declared exceptions (throws clause) AND thrown exceptions from method bodies | Declared throws only covers checked exceptions. The surprises at runtime are unchecked exceptions thrown internally. Knowing a method throws `IllegalStateException("Connection pool exhausted")` before hitting it at runtime is the proactive insight that makes the tool pay for itself. | Declared throws only (misses the exceptions that actually surprise you) |
| 7 | Index files live in project `.jackknife/index/`, not next to jars in `~/.m2` | Rich structural index may be large and format may evolve. Project-scoped means format changes don't require clearing global indexes. Plugin regenerates from resolved classpath — fast. | Next to jars in `~/.m2` (format changes require global cleanup, "pollutes" repo layout) |
| 8 | SNAPSHOT invalidation via timestamp comparison | If index file is older than jar file, regenerate. Released artifacts never change so index is permanently valid. SNAPSHOTs re-index on new builds — acceptable cost for development artifacts. | Checksum-based (more complex, same result), always regenerate (wasteful for releases) |
| 9 | Drop shared knowledge artifact (former Accounting capability) | Cross-session collision risk outweighs value. Claude's memory system already handles cross-session knowledge. One less concurrency problem. | Shared field journal (collisions between concurrent sessions) |
| 10 | Flat groupId in directory paths (`org.apache.httpcomponents/` not `org/apache/httpcomponents/`) | Avoids ambiguity of undetermined directory depth. Maven only went to dots-as-dirs because of filesystem limitations at maven central scale — we don't have that problem. | Exploded groupId dirs (ambiguous depth, harder to resolve back to groupId) |
| 11 | Plugin runs automatically on every build — no explicit goals needed for instrument+swap | Filesystem is the state machine. Drop a file in instrument/, next build picks it up. No extra commands to remember. Plugin no-ops when dirs are empty. | Explicit goals (extra step to remember, breaks the "just build" workflow) |
| 12 | instrument/ → modified/ lifecycle: process moves config, acts as receipt | instrument/ stays clean (inbox empties). modified/ contains both patched jar and the config that produced it. Revert by deleting from modified/. No re-instrumentation on subsequent builds. | Delete config after processing (lose the receipt), keep in instrument/ (re-instruments every build) |
| 13 | Plugin self-generates usage docs to `.jackknife/USAGE.md` (or similar) | Skills have a maintenance problem — docs and tool drift apart. Plugin generates its own docs from its internals, always version-matched. Global CLAUDE.md just says "run index, read the usage file." 3 lines, never needs updating. | Hand-maintained skill file (drifts from tool), detailed CLAUDE.md entry (maintenance burden) |
| 14 | Index goal also writes the usage file — one command bootstraps everything | First command in a session builds indexes AND provides the instruction manual. No second step to remember. | Separate `usage` goal (extra step), write usage on every goal (wasteful) |
| 15 | After initial index, read-only operations (search, discovery) use Grep on index files directly — no Maven | Avoids 3-5s Maven startup overhead on the most frequent operations (80% of interactions). Index files are flat greppable text in `.jackknife/index/`. Maven only needed for index build, decompile, and builds that the user is already running. | All operations through Maven goals (3-5s overhead per invocation, 10 lookups = 30-50s wasted) |
| 16 | Index includes access modifiers (`public`, `private`, `static`, etc.) on methods and fields | Needed for writing correct calling code and understanding instrumentation targets. Private methods are still instrumentable but not callable directly. | Omit modifiers (loses information needed for writing code) |
| 17 | Index includes generics (e.g., `java.util.Set<java.lang.Class<? extends java.io.IOException>>`) | Accurate type information, greppable for generic usages. Verbose but correct. | Erased types only (loses type parameter information) |
| 18 | Plugin goal writes Snitch properties file, not Claude | Plugin knows Snitch format natively. Eliminates format transform errors (arrays, generics, inner classes). Claude just specifies class, method, mode. | Claude writes Snitch files directly (95% reliable, but failures waste time debugging format instead of actual problem), custom format in instrument/ (two formats to maintain) |
| 19 | Plugin's method parser is forgiving — accepts index format, Snitch format, or anything in between | Claude can paste what it sees in the index. Plugin strips modifiers, parameter names, return type, extra whitespace. Eliminates "formatted it wrong" errors. | Strict format only (error-prone when copy-pasting from index output) |
| 20 | Method-name-only instrumentation targets all matching methods across classes | "Instrument `retryRequest` everywhere" — useful for interfaces with multiple implementations. Plugin uses index to resolve all matches. Can narrow with `-Dimplements=` or `-Dpackage=`. | Require exact FQN class+method always (tedious for cross-cutting concerns) |
| 21 | Instrument goal executes without confirmation, prints clear summary of what it matched | No interactive confirmation — just do it, show what was done. Claude reads the output, cleans up from modified/ if too broad. Faster flow, no blocking prompts. | Confirm before writing (slows down the workflow, adds interactivity) |
| 22 | Two instrumentation modes: `debug` (entry args + exit return/exception) and `timing` (elapsed time only) | No decision paralysis. Debug = everything for behavior analysis. Timing = lightweight for perf work, value logging would skew measurements. | Separate args/return/all modes (unnecessary granularity), tracking mode (least useful of Snitch's original modes) |
| 23 | Snitch enhanced to delegate to java.lang.reflect.InvocationHandler | Snitch becomes just the bytecode wrapping engine (rename + delegate). All behavior (timing, arg logging, threshold, file output, value modification) lives in plain Java handler code. No ASM changes needed per feature. Same pattern as EJB interceptors / CDI InvocationContext. | Hardcoded mode-specific bytecode generation in Snitch (new ASM work for every feature), separate Snitch modes for each behavior (inflexible) |
| 24 | Two artifacts: plugin (heavy, plugin-classloader-isolated) and runtime jar (small, shaded like Churchkey) | Plugin has Vineflower/ASM/Archie/Snitch — isolated by Maven's plugin classloader. Runtime jar has handler impls + tomitribe-util shaded — added to project classpath by plugin. Clean separation of build-time vs run-time concerns. | Single artifact (classloader conflicts), pure JDK runtime (too constraining for real formatting/IO code) |
| 25 | Runtime config via classpath properties file (`META-INF/jackknife.properties`) | Plugin writes captures dir, threshold, mode into properties file in runtime jar or patched jar. Handler reads on first invocation, caches. No constructor args, no system properties, no static init. | System properties (must remember to set), constructor injection (bytecode complexity), hardcoded paths (inflexible) |
| 26 | Large captured values written to file, referenced in console output | Method returns a 50KB HTML doc — dumping to stdout buries everything. Write to `target/jackknife/captures/ClassName-method-NNN.txt`, console shows file reference. Claude reads the file directly for test assertions — eliminates System.out/println/parse cycles. | Always inline (buries output), always to file (loses convenience for small values) |
| 27 | Threshold-based: inline if total line length over threshold or value contains newlines | Short values stay readable in console. Long values get filed. Sensible default, configurable, probably never touched. | Fixed behavior either way (either too noisy or too indirect) |
| 28 | Instrument project code too, not just dependencies | Same Snitch+InvocationHandler applied to `target/classes/` after compile. Better than print statements: no source modification, no dirty diffs, no forgotten debug prints, structured output with threshold. Simpler than jar case — no Archie, no Artifact.setFile(), `mvn clean` reverts. | Print statements (modify source, forget to remove, dirty diffs), debugger only (not always available or practical) |
| 29 | Project code configs stay in instrument/ (not moved); dependency configs move to modified/ | Project instrumentation targets `target/classes/` which is ephemeral — config must persist for re-application each compile. Dependency instrumentation produces a persistent jar in modified/ — config moves as receipt. Plugin determines which by checking if the class belongs to the project or a dependency. One inbox, no extra directories. | Separate directory for project configs (unnecessary complexity), always move (breaks project re-application), never move (loses receipt for dependencies) |
| 30 | Plugin declared in parent pom profile, auto-activated by `.jackknife/` directory existence | `<activation><file><exists>.jackknife</exists></file></activation>` — filesystem-as-state-machine again. Plugin has full capabilities (classpath, Artifact.setFile()) when active. `rm -rf .jackknife/` deactivates completely. Parent pom is set-and-forget. Combined with `<pluginGroups>` in settings.xml for `mvn jackknife:index` shorthand. | Per-project pom declaration (tedious), settings.xml only (can't bind to lifecycle), always-on in parent (unnecessary overhead when not used) |
| 31 | Cache decompiled source in `.jackknife/source/` | Avoids repeat Maven+Vineflower invocations for the same class. Released artifacts never change. SNAPSHOTs use same timestamp invalidation as index. Cheap disk, expensive repeat decompilation. | No caching (repeat 3-5s Maven cost for same class), target/ only (lost on clean) |
| 32 | Index includes non-class resources (META-INF/services, config files, etc.) | Resources affect behavior as much as code. Listed under `# resources` header to avoid confusion with class entries when grepping. Simple file listing, no content — extract from jar if needed. | Class-only index (misses config-driven behavior) |

## Open Questions

### Resolved
- [x] User-facing class names — **dotted** (`com.example.Foo`), fully qualified everywhere in index
- [x] Instrumentation granularity — **two modes**: `debug` (args + return + exception) and `timing`
- [x] Usage file regeneration strategy — always regenerate, with "do not edit — generated" header
- [x] Index file format — flat text, greppable, FQN class names, modifiers, generics, declared + thrown exceptions
- [x] Instrumentation config format — plugin writes Snitch properties files via `instrument` goal with forgiving method parser
- [x] Naming: project=jackknife, plugin=jackknife-maven-plugin, runtime=jackknife-runtime, dir=.jackknife/, groupId=org.tomitribe.jackknife
- [x] Runtime jar — small, shaded (tomitribe-util relocated like Churchkey), real code for formatting/IO/threshold
- [x] Plugin shading — plugin classloader isolates it; runtime jar shaded separately
- [x] Decompile caching — yes, cache in `.jackknife/source/` (decision 31). Same timestamp invalidation as index for SNAPSHOTs.

### Deferred to implementation
These are implementation details that will be resolved when writing the code, not design-level questions:
- Maven plugin lifecycle phase for automatic process+swap — likely `process-classes` for project code, `initialize` for dependency swap
- Multi-module `.jackknife/` location — walk up from module to find project root, or use `${session.executionRootDirectory}`
- Dir proxy interface design — emerges from the code
- Handler registration mechanism — likely global registry keyed by class+method
- Large value threshold default — pick a sensible number (e.g., 500 chars or presence of newlines), tune from experience
- `target/jackknife/captures/` naming convention — straightforward, e.g., `ClassName-method-NNN.txt`

### Explicitly deferred
- CLI extraction — build plugin-only first, extract CLI later if Maven startup overhead is a real problem (decision 3). If extracted: brew install, global PATH, `Bash(jackknife *)` permission.

## Rejected Alternatives
| Alternative | Why Rejected |
|-------------|-------------|
| Classifier strategy (new jar + pom dep changes + excludes) | Cascading excludes for deeply transitive deps eat more time than debugging saves |
| In-place jar overwrite in `~/.m2` | Requires backup/restore complexity; Artifact.setFile() is cleaner |
| Separate `.index/` directory tree in `~/.m2` | Two trees to keep in sync, stale entries when jars deleted |
| Index files next to jars in `~/.m2` | Format may evolve; changes require clearing global indexes; "pollutes" repo |
| Central index-of-indexes file | Concurrency issues across multiple working sessions |
| Full upfront indexing of `~/.m2` | Too slow, indexes things you never use |
| CLI-only approach (no plugin) | Slower (has to guess scope or walk entire repo), no access to resolved classpath |
| Relative path CLI approval (`./.mydir/mycli`) | Claude Code permission system uses literal string matching — breaks across working directory changes |
| Plugin as CLI distribution channel | Maven artifacts have no executable bits, path changes between versions, can't pre-approve |
| Shared accounting/knowledge file across sessions | Collision risk between concurrent sessions; Claude memory system handles cross-session knowledge better |
| SKIP_DEBUG in ASM scanning | Loses parameter names from LocalVariableTable/MethodParameters — makes index much less useful |
| SKIP_CODE in ASM scanning | Misses exceptions thrown inside method bodies — the ones that actually surprise you at runtime |
| Exploded groupId directories (`org/apache/httpcomponents/`) | Ambiguous directory depth, harder to resolve back to groupId; Maven only did this for scale we don't have |
| Single config file at `.jackknife/` root | Doesn't encode GAV; can't tell which artifact to instrument without parsing content |
| Simple class names in index (not fully qualified) | Looks cleaner but loses greppability — can't search for FQN, can't distinguish `com.foo.Foo` from `com.bar.Foo` |
| Erased generics in index | Loses type parameter information; `Set` vs `Set<Class<? extends IOException>>` matters for writing correct code |
| Omit access modifiers from index | Loses information needed for writing correct calling code; need to know public/private/static |
| Declared throws only (omit in-body throws) | Misses the unchecked exceptions that actually surprise you at runtime |
| Hand-maintained skill file for tool documentation | Drifts from tool over time; maintenance burden; plugin self-generating USAGE.md is always version-matched |
| Claude writes Snitch properties files directly | 95% reliable but format errors waste time debugging config instead of actual problem; plugin knows the format natively |
| Custom file format in instrument/ dir (not Snitch format) | Two formats to maintain; plugin writes Snitch format so instrument/ doesn't need its own schema |
| Strict method signature parsing (Snitch format only) | Error-prone when copy-pasting from index output; forgiving parser handles any reasonable input |
| Require exact FQN class+method for instrumentation | Tedious for cross-cutting concerns; method-name-only targets all implementations (e.g., an interface with 10 impls) |
| Interactive confirmation before instrumenting | Slows down workflow; clear summary output is sufficient; can clean up from modified/ if too broad |
| Separate args/return/all instrumentation modes | Unnecessary granularity; `debug` (everything) and `timing` (lightweight) cover the real use cases |
| Snitch tracking mode (scoped nested call grouping) | Least useful of original modes; debug + timing covers our needs |
| Hardcoded mode-specific bytecode generation in Snitch | Requires new ASM work for every feature; InvocationHandler delegation keeps Snitch simple, behavior in plain Java |
| Always inline captured values in console output | Large return values (e.g., 50KB HTML) bury everything; threshold-based file output keeps console readable |
| Always write captured values to file | Loses convenience for small values; inline for short, file for long is the right balance |
| Inject handler classes directly into patched jar | Handler travels with instrumented code but is harder to change without re-instrumenting |
| Use only JDK classes in generated bytecode (no handler) | Loses InvocationHandler elegance; back to mode-specific bytecode generation |
| Nested artifact/version directories under groupId | Extra depth for cleanup benefit that glob (`artifact-version.jar*`) already provides |

## Action Items
| # | Item | Dependencies | Issue |
|---|------|-------------|-------|
