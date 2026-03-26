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

// Verify that instrumenting a test-scope dependency produces debug output
// when the tests exercise that dependency.

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// -- Build should succeed --
assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'

// -- Tests should have run --
assert logContent.contains('Tests run:') : 'Tests should have run'
assert !logContent.contains('Failures: 1') : 'No test failures expected'

// -- ProcessMojo should have transformed the jar --
assert logContent.contains('Transforming') || logContent.contains('Swapped') :
    'ProcessMojo should transform or swap the instrumented jar'

// -- JACKKNIFE registration should appear --
assert logContent.contains('"event":"register"') :
    'HandlerRegistry should log registration event'

// -- JSON call events should appear during test execution --
assert logContent.contains('"event":"call"') :
    'Debug output should include call events'

assert logContent.contains('"class":"Join"') :
    'Debug output should reference the Join class'

assert logContent.contains('"method":"join"') :
    'Debug output should reference the join method'

assert logContent.contains('"return":') :
    'Debug output should include return values'

assert logContent.contains('"time":') :
    'Debug output should include timing'

// -- Modified jar should exist --
def modifiedDir = new File(basedir, '.jackknife/modified')
assert modifiedDir.exists() : 'modified/ should exist'

def patchedJars = []
modifiedDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name.endsWith('.jar')) patchedJars.add(f)
}
assert patchedJars.size() > 0 : 'Should have at least one patched jar'

// -- Patched jar should contain handlers.properties --
def jar = new java.util.jar.JarFile(patchedJars[0])
def handlerConfig = jar.getEntry('META-INF/jackknife/handlers.properties')
assert handlerConfig != null : 'Patched jar should contain handler config'
jar.close()

println "single-instrument-test-dependency verified:"
println "  Build succeeded: yes"
println "  Tests ran: yes"
println "  Jar transformed: yes"
println "  Handler registered: yes"
println "  Debug ENTER/EXIT output: yes"
