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

// Verify multi-module project code instrumentation:
// 1. .jackknife/ at reactor root only
// 2. Both modules' classes enhanced
// 3. Debug output from both modules' tests

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// -- Build should succeed --
assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'

// -- .jackknife/ at reactor root, not in modules --
def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife should exist at reactor root'

def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

// -- Both modules' classes should be enhanced --
assert logContent.contains('com.example.a.ServiceA') : 'Should enhance ServiceA in module-a'
assert logContent.contains('com.example.b.ServiceB') : 'Should enhance ServiceB in module-b'

// -- Enhance goal should run for both modules --
def enhanceCount = (logContent =~ /Enhanced \d+ class/).count
assert enhanceCount >= 2 : "Enhance goal should run for both modules (found ${enhanceCount})"

// -- Debug output from module-a tests --
assert logContent.contains('"class":"ServiceA"') : 'Should have debug output for ServiceA'
assert logContent.contains('"method":"process"') : 'Should have debug output for process method'

// -- Debug output from module-b tests --
assert logContent.contains('"class":"ServiceB"') : 'Should have debug output for ServiceB'
assert logContent.contains('"method":"transform"') : 'Should have debug output for transform method'

// -- Return values visible --
assert logContent.contains('"return":"A:HELLO"') : 'Should see ServiceA return value'
assert logContent.contains('"return":"B:hello"') : 'Should see ServiceB return value'

// -- handlers.properties in both modules' target/classes --
def handlersA = new File(basedir, 'module-a/target/classes/META-INF/jackknife/handlers.properties')
assert handlersA.exists() : 'handlers.properties should be in module-a target/classes'

def handlersB = new File(basedir, 'module-b/target/classes/META-INF/jackknife/handlers.properties')
assert handlersB.exists() : 'handlers.properties should be in module-b target/classes'

println "multi-instrument-mainclass verified:"
println "  .jackknife/ at reactor root: yes"
println "  ServiceA enhanced + debug output: yes"
println "  ServiceB enhanced + debug output: yes"
println "  handlers.properties in both modules: yes"
