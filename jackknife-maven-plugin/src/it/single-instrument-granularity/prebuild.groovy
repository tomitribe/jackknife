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

// Tests all three matching granularity levels:
// (a) Full signature: className+methodName+params  -> one method
// (b) className+methodName                         -> all overloads
// (c) className only                               -> all methods
//
// After each step, verify config was created. After (c), verify * marker.
// Leave state after (b) so the main build tests className+methodName.

def mvnCmd = System.getProperty('os.name').toLowerCase().contains('win') ? 'mvn.cmd' : 'mvn'

// Generate a settings.xml pointing to the IT local repo
def localRepo = new File(basedir.parentFile.parentFile, 'local-repo')
def settingsFile = new File(basedir, 'settings.xml')
settingsFile.text = "<settings><localRepository>${localRepo.absolutePath}</localRepository></settings>"
def settingsArg = ['-s', settingsFile.absolutePath]

/**
 * Run a Maven command in the project directory and assert it succeeds.
 */
def runMaven = { String label, List<String> args ->
    def cmd = [mvnCmd] + settingsArg + args + ['-B']
    println "  [${label}] ${cmd.join(' ')}"
    def proc = new ProcessBuilder(cmd)
        .directory(basedir)
        .redirectErrorStream(true)
        .start()
    proc.inputStream.eachLine { println "    [${label}] ${it}" }
    def exit = proc.waitFor()
    assert exit == 0 : "${label} failed with exit code ${exit}"
}

def instrumentDir = new File(basedir, '.jackknife/instrument')

/**
 * Find all .properties config files under .jackknife/instrument/.
 */
def findConfigFiles = {
    def configs = []
    if (instrumentDir.exists()) {
        instrumentDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
            if (f.name.endsWith('.properties')) configs.add(f)
        }
    }
    return configs
}

// ====================================================================
// Step 1: Index
// ====================================================================
println "Step 1: jackknife:index"
runMaven('index', ['jackknife:index'])

// ====================================================================
// Step 2a: Full signature — one specific overload
// ====================================================================
println "Step 2a: instrument with full signature (one method)"
runMaven('instrument-full', [
    'jackknife:instrument',
    '-Dmethod=org.tomitribe.util.Join.join(java.lang.String,java.util.Collection)',
    '-Dmode=debug'
])

def configsA = findConfigFiles()
assert configsA.size() > 0 : '(a) Should have at least one instrument config file'

// Read the config content — should have the full signature
def contentA = configsA[0].text
assert contentA.contains('join(java.lang.String,java.util.Collection)') :
    '(a) Config should contain the full method signature'
println "  (a) Full signature: PASS — config contains exact signature"

// ====================================================================
// Step 2b: Clean, re-index, then className+methodName (all overloads)
// ====================================================================
println "Step 2b: clean, re-index, instrument with className+methodName"
runMaven('clean-b', ['jackknife:clean'])
runMaven('index-b', ['jackknife:index'])
runMaven('instrument-name', [
    'jackknife:instrument',
    '-Dclass=org.tomitribe.util.Join',
    '-Dmethod=join',
    '-Dmode=debug'
])

def configsB = findConfigFiles()
assert configsB.size() > 0 : '(b) Should have at least one instrument config file'

// Should have className.methodName without params
def contentB = configsB[0].text
assert contentB.contains('Join.join') :
    '(b) Config should contain Join.join'
println "  (b) className+methodName: PASS — config targets all overloads of join"

// ====================================================================
// Step 2c: Clean, re-index, then className only (all methods)
// ====================================================================
println "Step 2c: clean, re-index, instrument with className only"
runMaven('clean-c', ['jackknife:clean'])
runMaven('index-c', ['jackknife:index'])
runMaven('instrument-class', [
    'jackknife:instrument',
    '-Dclass=org.tomitribe.util.Join',
    '-Dmode=debug'
])

def configsC = findConfigFiles()
assert configsC.size() > 0 : '(c) Should have at least one instrument config file'

// className-only should produce * (all methods marker)
def contentC = configsC[0].text
assert contentC.contains('*') :
    '(c) Config should contain * (all methods marker)'
println "  (c) className only: PASS — config contains * for all methods"

// ====================================================================
// Step 3: Leave state after (b) for the main build
// ====================================================================
println "Step 3: reset to className+methodName state for main build"
runMaven('clean-final', ['jackknife:clean'])
runMaven('index-final', ['jackknife:index'])
runMaven('instrument-final', [
    'jackknife:instrument',
    '-Dclass=org.tomitribe.util.Join',
    '-Dmethod=join',
    '-Dmode=debug'
])

def configsFinal = findConfigFiles()
assert configsFinal.size() > 0 : 'Final state should have instrument config'
println "Prebuild complete: all three granularity levels verified"
println "  Left in className+methodName state for main build"
