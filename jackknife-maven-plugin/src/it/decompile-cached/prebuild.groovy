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

// Run decompile once so the cache is warm for the main build.

def mvnCmd = System.getProperty('os.name').toLowerCase().contains('win') ? 'mvn.cmd' : 'mvn'

// Generate a settings.xml pointing to the IT local repo
def localRepo = new File(basedir.parentFile.parentFile, 'local-repo')
def settingsFile = new File(basedir, 'settings.xml')
settingsFile.text = "<settings><localRepository>${localRepo.absolutePath}</localRepository></settings>"
def settingsArg = ['-s', settingsFile.absolutePath]

def cmd = [mvnCmd] + settingsArg + ['jackknife:decompile', '-Dclass=org.tomitribe.jackknife.runtime.DebugHandler', '-B']
println "Prebuild: running ${cmd.join(' ')}"

def proc = new ProcessBuilder(cmd)
    .directory(basedir)
    .redirectErrorStream(true)
    .start()

proc.inputStream.eachLine { println "  [prebuild] ${it}" }
def exitCode = proc.waitFor()
assert exitCode == 0 : "Prebuild decompile failed with exit code ${exitCode}"

// Verify source was created
def sourceDir = new File(basedir, '.jackknife/source')
assert sourceDir.exists() : 'Source dir should exist after prebuild'
