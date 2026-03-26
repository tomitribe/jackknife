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

// Verify that instrumenting a project class in src/main produces debug output.

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// -- Build should succeed --
assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'

// -- Tests should have run --
assert logContent.contains('Tests run:') : 'Tests should have run'
assert !logContent.contains('Failures: 1') : 'No test failures expected'

// -- EnhanceClassesMojo should have enhanced the class --
assert logContent.contains('Enhanced') : 'Should log enhanced classes'
assert logContent.contains('com.example.Helper') : 'Should enhance Helper class'

// -- JACKKNIFE registration should appear --
assert logContent.contains('"event":"register"') :
    'HandlerRegistry should log registration event'

// -- JSON call events should appear during test execution --
assert logContent.contains('"event":"call"') :
    'Debug output should include call events'

assert logContent.contains('"class":"Helper"') :
    'Debug output should reference the Helper class'

assert logContent.contains('"method":"format"') || logContent.contains('"method":"greet"') :
    'Debug output should reference instrumented methods'

assert logContent.contains('"return":') :
    'Debug output should include return values'

// -- handlers.properties should be written to target/classes --
def handlersFile = new File(basedir, 'target/classes/META-INF/jackknife/handlers.properties')
assert handlersFile.exists() : 'handlers.properties should be in target/classes'
def handlersContent = handlersFile.text
assert handlersContent.contains('com.example.Helper') : 'handlers.properties should reference Helper'

println "single-instrument-mainclass verified:"
println "  Build succeeded: yes"
println "  Classes enhanced: yes"
println "  Handler registered: yes"
println "  Debug output: yes"
println "  handlers.properties in target/classes: yes"
