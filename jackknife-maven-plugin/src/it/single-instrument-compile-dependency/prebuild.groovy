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

// Simulate the user workflow:
// 1. Run jackknife:index to build manifests
// 2. Run jackknife:instrument to instrument Join.join with debug mode

def mvnCmd = System.getProperty('os.name').toLowerCase().contains('win') ? 'mvn.cmd' : 'mvn'

// Generate a settings.xml pointing to the IT local repo
def localRepo = new File(basedir.parentFile.parentFile, 'local-repo')
def settingsFile = new File(basedir, 'settings.xml')
settingsFile.text = "<settings><localRepository>${localRepo.absolutePath}</localRepository></settings>"
def settingsArg = ['-s', settingsFile.absolutePath]

// Step 1: index
def indexCmd = [mvnCmd] + settingsArg + ['jackknife:index', '-B']
println "Prebuild step 1: ${indexCmd.join(' ')}"

def indexProc = new ProcessBuilder(indexCmd)
    .directory(basedir)
    .redirectErrorStream(true)
    .start()

indexProc.inputStream.eachLine { println "  [index] ${it}" }
def indexExit = indexProc.waitFor()
assert indexExit == 0 : "jackknife:index failed with exit code ${indexExit}"

// Step 2: instrument Join.join(String,Collection) with debug mode
def instrumentCmd = [mvnCmd] + settingsArg + [
    'jackknife:instrument',
    '-Dmethod=org.tomitribe.util.Join.join(java.lang.String,java.util.Collection)',
    '-Dmode=debug',
    '-B'
]
println "Prebuild step 2: ${instrumentCmd.join(' ')}"

def instrumentProc = new ProcessBuilder(instrumentCmd)
    .directory(basedir)
    .redirectErrorStream(true)
    .start()

instrumentProc.inputStream.eachLine { println "  [instrument] ${it}" }
def instrumentExit = instrumentProc.waitFor()
assert instrumentExit == 0 : "jackknife:instrument failed with exit code ${instrumentExit}"

// Verify instrument config was created
def instrumentDir = new File(basedir, '.jackknife/instrument')
assert instrumentDir.exists() : 'instrument dir should exist after jackknife:instrument'

def configFiles = []
instrumentDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name.endsWith('.properties')) configFiles.add(f)
}
assert configFiles.size() > 0 : 'Should have at least one instrument config file'
println "Prebuild complete: ${configFiles.size()} instrument config(s) created"
