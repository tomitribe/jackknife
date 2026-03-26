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

// Verify instrumenting a test class (src/test) produces debug output.

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

assert logContent.contains('BUILD SUCCESS') : 'Build should succeed'
assert logContent.contains('Tests run:') : 'Tests should have run'
assert !logContent.contains('Failures: 1') : 'No test failures expected'

// EnhanceClassesMojo should enhance the test class
assert logContent.contains('Enhanced') : 'Should log enhanced classes'
assert logContent.contains('com.example.TestHelper') : 'Should enhance TestHelper'

// Debug JSON output should appear
assert logContent.contains('"event":"register"') : 'Should have register event'
assert logContent.contains('"event":"call"') : 'Should have call events'
assert logContent.contains('"class":"TestHelper"') : 'Should reference TestHelper class'
assert logContent.contains('"method":"compute"') || logContent.contains('"method":"format"') :
    'Should reference instrumented methods'
assert logContent.contains('"return":') : 'Should include return values'

// handlers.properties should be in target/test-classes or target/classes
def handlersInClasses = new File(basedir, 'target/classes/META-INF/jackknife/handlers.properties')
def handlersInTestClasses = new File(basedir, 'target/test-classes/META-INF/jackknife/handlers.properties')
assert handlersInClasses.exists() || handlersInTestClasses.exists() :
    'handlers.properties should be in target/classes or target/test-classes'

println "single-instrument-testclass verified"
