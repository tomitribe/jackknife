# Jackknife — Test Plan

**Status: 164 unit tests + 6 integration tests, 0 failures**
- Runtime: 70 tests (ProceedHandler, DebugHandler, TimingHandler, HandlerRegistry)
- Plugin: 94 tests (HandlerEnhancer, MethodParser, ProcessMojoConfig, EndToEnd, etc.)
- ITs: 6 (index-basic, index-rerun, process-basic, decompile-basic, decompile-cached, multi-module)

## HandlerEnhancer (bytecode transformation)

### Method shapes
- [x] Instance method with return value
- [x] Instance void method
- [x] Static method with return value
- [x] Static void method
- [x] Method returning primitive (int, long, boolean, double, byte, short, char, float)
- [x] Method returning array (Object[], int[], byte[])
- [x] Method with no parameters
- [x] Method with one parameter
- [x] Method with many parameters
- [x] Method with varargs
- [x] Method with generic parameters (List<String>, Map<K,V>)
- [x] Method with generic return type
- [x] Synchronized method (wrapper should drop synchronized)
- [x] Method that throws checked exception
- [x] Method that throws unchecked exception
- [x] Overloaded methods (same name, different args — all get wrapped)
- [x] Private method
- [x] Protected method
- [x] Package-private method
- [x] Final method
- [x] Constructor (should NOT be wrapped)
- [x] Static initializer (should NOT be wrapped)

### Annotations
- [x] Annotations move from renamed method to wrapper
- [x] Parameter annotations move to wrapper
- [x] Multiple annotations on same method

### Decompile verification
- [x] Every transformed class decompiles successfully with Vineflower
- [x] Decompiled wrapper shows HandlerRegistry.getHandler call
- [x] Decompiled wrapper shows null check with fallback to direct call
- [x] Decompiled wrapper shows Object[] arg boxing
- [x] Decompiled wrapper shows handler.invoke call

## ProceedHandler (reflection-based proceed)

- [x] Instance method — calls renamed method on target
- [x] Static method — calls renamed method with null target
- [x] Void method — returns null
- [x] Primitive return — boxes correctly (all 8 primitives)
- [x] Object return — passes through
- [x] Method throws checked exception — unwraps InvocationTargetException, rethrows cause
- [x] Method throws unchecked exception — unwraps and rethrows
- [x] Method with all primitive param types — reflection handles unboxing
- [x] Overloaded methods — finds correct renamed method by param types
- [x] Method cache — second call uses cache, doesn't re-lookup
- [x] Missing renamed method — throws clear error

## DebugHandler

- [x] Logs ENTER with args for instance method
- [x] Logs ENTER with args for static method (null proxy, no NPE)
- [x] Logs EXIT with return value
- [x] Logs EXIT with null return value
- [x] Logs EXIT with primitive return value (boxed)
- [x] Logs THROW with exception class and message
- [x] Re-throws original exception (not wrapped)
- [x] Delegates to next handler in chain
- [x] Array args formatted correctly (Object[], int[], byte[] shows length)
- [x] Null arg formatted as "null"
- [x] Short values inline in console
- [x] Value exactly at threshold — stays inline
- [x] Value one char over threshold — written to capture file
- [x] Short value with newlines — goes to file (newline trigger independent of length)
- [x] Args individually short but total line exceeds threshold — goes to file
- [x] Capture file content matches the full value (not truncated)
- [x] Capture file reference format is clear and parseable (can parse path from output)
- [x] Capture directory created if not exists
- [x] Capture file naming is sequential and predictable

## TimingHandler

- [x] Logs TIMING with elapsed time
- [x] Formats nanoseconds correctly (ns, us, ms, s)
- [x] Static method (null proxy, no NPE)
- [x] Delegates to next handler in chain
- [x] Exception in delegate still logs timing (finally block)

## HandlerRegistry

- [x] getHandler returns null when no handler registered
- [x] getHandler returns handler after register
- [x] Auto-discovers META-INF/jackknife/handlers.properties on classpath
- [x] Parses debug mode correctly
- [x] Parses timing mode correctly
- [x] Builds correct handler chain: debug = DebugHandler(ProceedHandler)
- [x] Builds correct handler chain: timing = TimingHandler(ProceedHandler)
- [x] Builds correct handler chain: all = TimingHandler(DebugHandler(ProceedHandler))
- [x] Handles empty paramTypes (no-arg method)
- [x] Handles multiple paramTypes (comma-separated)
- [x] Resolves primitive type names (int, long, boolean, etc.)
- [x] Resolves fully qualified class names
- [x] Multiple handlers.properties files on classpath (multiple jars)
- [x] Skips comment lines and blank lines
- [x] clear() resets initialization state
- [x] Thread-safe: concurrent getHandler calls

## MethodParser

- [x] Snitch format: com.example.Foo.process(java.lang.String,int)
- [x] Index format with modifiers: public method process(java.lang.String input, int count) : boolean
- [x] Index format with annotations: @jakarta.inject.Inject public method process(...)
- [x] Method name only: process
- [x] Method name with class: -Dclass=com.example.Foo -Dmethod=process
- [x] Method with args no class: process(int, String)
- [x] Empty parens: process()
- [x] Static modifier stripped
- [x] Multiple modifiers stripped: public static final method
- [x] Return type stripped: : boolean
- [x] Simple type expansion: String -> java.lang.String
- [x] Simple type expansion: List -> java.util.List
- [x] FQN class detected in method string
- [x] No class in method string returns null className

## End-to-End (transform + register + invoke + verify)

- [x] Transform a class, put handler config on classpath, call method, verify ENTER/EXIT output
- [x] Static method end-to-end
- [x] Void method end-to-end
- [x] Primitive return end-to-end
- [x] Exception thrown end-to-end — verify THROW output and exception propagates
- [x] Timing mode end-to-end — verify TIMING output
- [x] Debug mode end-to-end — verify ENTER + EXIT output
- [x] Handler chain: TimingHandler(DebugHandler(ProceedHandler)) — verify both outputs
- [x] No handler registered — method executes normally (fallback to direct call)
- [x] Large return value — verify capture file written and referenced
- [x] Multiple methods instrumented in same class
- [x] Overloaded methods instrumented end-to-end

## IndexMojo (manifest generation) — via IT: index-basic, index-rerun

- [x] Generates manifest file per jar
- [x] Manifest contains all class names (dotted format)
- [x] Manifest contains resources under # resources header
- [x] Skips module-info.class and package-info.class
- [ ] Skips project's own artifact (uber jar awareness)
- [x] SNAPSHOT invalidation: re-manifests if jar is newer (index-rerun IT)
- [x] Released jar: skips if manifest exists (index-rerun IT)
- [x] Generates USAGE.md
- [x] Creates .jackknife/manifest/ directory structure
- [x] Flat groupId directories

## DecompileMojo — via IT: decompile-basic, decompile-cached

- [x] Decompiles entire jar on first request
- [x] Writes one .java file per class
- [x] Returns cached source on repeat request (no re-decompile) (decompile-cached IT)
- [ ] SNAPSHOT invalidation: re-decompiles if jar is newer
- [x] Correct class returned (not similarly-named class)
- [ ] Inner classes decompiled correctly
- [ ] Prints requested class to stdout
- [x] Source directory structure matches package structure

## ProcessMojo — via IT: process-basic + unit tests

- [x] Parses @ prefix as debug mode
- [x] Parses no prefix as timing mode
- [x] Extracts class name and method name from FQN spec
- [x] Transforms jar: matching classes enhanced, others copied through
- [x] Injects META-INF/jackknife/handlers.properties into patched jar
- [x] Config toHandlerConfig() generates correct format
- [x] Moves properties from instrument/ to modified/
- [x] No-ops when .jackknife/ doesn't exist
- [x] No-ops when instrument/ is empty
- [ ] Swaps patched jar via Artifact.setFile()
- [x] Non-class entries (resources) copied through unchanged

## Multi-Module vs Single-Module — via IT: multi-module

### Capture file location
- [ ] Single module — captures written to `target/jackknife/captures/`
- [ ] Multi-module — captures written to correct module's `target/jackknife/captures/`
- [ ] Multi-module — two modules both instrumented, captures don't collide

### .jackknife/ location
- [x] Single module — `.jackknife/` at project root (all single-module ITs)
- [x] Multi-module — `.jackknife/` at reactor root, shared across modules (multi-module IT)
- [x] Multi-module — modified/ at reactor root (multi-module IT)

### Dependency instrumentation
- [x] Single module — instrument a dependency jar, patched jar swapped in (process-basic IT)
- [x] Multi-module — instrument a dependency used by multiple modules, swapped in for all (multi-module IT)
- [ ] Multi-module — instrument a dependency used by only one module

### Project code instrumentation
- [ ] Single module — instrument project code in `target/classes/`
- [ ] Multi-module — instrument code in one module's `target/classes/`
- [ ] Multi-module — instrument code in sibling module (inter-module dependency)

### Index/manifest
- [x] Single module — manifests for all dependencies (index-basic IT)
- [ ] Multi-module — manifests cover all modules' dependencies (union) — **NOTE:** aggregator index on POM project doesn't see child module deps; design limitation
- [ ] Multi-module — no duplicate manifests for shared dependencies
