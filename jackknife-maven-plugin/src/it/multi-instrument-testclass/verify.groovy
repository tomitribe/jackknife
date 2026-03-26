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

// Verify multi-module test class instrumentation:
// 1. .jackknife/ at reactor root only
// 2. Both modules' test classes enhanced
// 3. Debug output from both modules' tests

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// -- Build should succeed --
assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'

// -- Tests should have run --
assert logContent.contains('Tests run:') : 'Tests should have run'
assert !logContent.contains('Failures: 1') : 'No test failures expected'

// -- .jackknife/ at reactor root, not in modules --
def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife should exist at reactor root'

def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

// -- Both modules' test classes should be enhanced --
assert logContent.contains('com.example.a.TestHelperA') : 'Should enhance TestHelperA in module-a'
assert logContent.contains('com.example.b.TestHelperB') : 'Should enhance TestHelperB in module-b'

// -- Debug JSON output should appear --
assert logContent.contains('"class":"TestHelperA"') : 'Should have debug output for TestHelperA'
assert logContent.contains('"class":"TestHelperB"') : 'Should have debug output for TestHelperB'

assert logContent.contains('"method":"compute"') || logContent.contains('"method":"format"') :
    'Should reference instrumented methods'

assert logContent.contains('"return":') : 'Should include return values'

println "multi-instrument-testclass verified:"
println "  .jackknife/ at reactor root: yes"
println "  TestHelperA enhanced + debug output: yes"
println "  TestHelperB enhanced + debug output: yes"
println "  Return values visible: yes"
