/*
 * Licensed to Tomitribe Corporation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. Tomitribe Corporation licenses
 * this file to You under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

// Verify multi-module behavior:
// 1. .jackknife/ lives at reactor root, not in child modules
// 2. ProcessMojo runs per-module, finding .jackknife/ at reactor root
// 3. Patched jar swapped for both modules

// -- .jackknife/ at reactor root, NOT in child modules --
def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife should exist at reactor root'

def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

// -- modified/ at reactor root --
def modifiedDir = new File(jackknife, 'modified')
assert modifiedDir.exists() : 'modified/ should exist at reactor root'

def modGroupDir = new File(modifiedDir, 'org.tomitribe.jackknife')
assert modGroupDir.exists() : 'modified groupId dir should exist'

// -- Patched jar created --
def patchedJars = modGroupDir.listFiles()?.findAll { it.name.endsWith('.jar') }
assert patchedJars != null && patchedJars.size() > 0 : 'Should have patched jar'

// -- Properties receipt moved --
def receipts = modGroupDir.listFiles()?.findAll { it.name.endsWith('.properties') }
assert receipts != null && receipts.size() > 0 : 'Should have properties receipt'

// -- instrument/ config should be moved (not still there) --
def instrumentGroupDir = new File(jackknife, 'instrument/org.tomitribe.jackknife')
def remainingProps = instrumentGroupDir.listFiles()?.findAll { it.name.endsWith('.properties') }
assert remainingProps == null || remainingProps.size() == 0 : 'Properties should be moved from instrument/ to modified/'

// -- Verify build.log shows ProcessMojo ran for BOTH modules and swapped --
def buildLog = new File(basedir, 'build.log')
def logContent = buildLog.text
def swapCount = (logContent =~ /Swapped \d+ modified jar/).count
assert swapCount >= 2 : "ProcessMojo should swap jars for both modules (found ${swapCount} swap messages)"

println "Multi-module verification passed:"
println "  .jackknife/ at reactor root: yes"
println "  .jackknife/ absent from modules: yes"
println "  modified/ at reactor root: yes"
println "  Jar swapped for both modules: yes (${swapCount} swaps)"
