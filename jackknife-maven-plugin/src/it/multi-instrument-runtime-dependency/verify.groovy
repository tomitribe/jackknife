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

// Verify that instrumenting a runtime-scope dependency in a multi-module
// reactor produces debug output when tests exercise that dependency.

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// -- Build should succeed --
assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'

// -- Tests should have run --
assert logContent.contains('Tests run:') : 'Tests should have run'
assert !logContent.contains('Failures: 1') : 'No test failures expected'

// -- .jackknife/ at reactor root, NOT in child modules --
def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife should exist at reactor root'

def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

// -- JACKKNIFE JSON output should contain class and method --
assert logContent.contains('"class":"Join"') || logContent.contains('"class": "Join"') :
    'Debug output should reference the Join class'

assert logContent.contains('"method":"join"') || logContent.contains('"method": "join"') :
    'Debug output should reference the join method'

println "multi-instrument-runtime-dependency verified:"
println "  Build succeeded: yes"
println "  Tests ran: yes"
println "  .jackknife/ at reactor root: yes"
println "  .jackknife/ absent from modules: yes"
println "  Join class in output: yes"
println "  join method in output: yes"
