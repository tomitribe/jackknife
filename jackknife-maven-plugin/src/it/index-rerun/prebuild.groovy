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

// Run jackknife:index once before the main build so we can test skip-on-rerun.

def mvnCmd = System.getProperty('os.name').toLowerCase().contains('win') ? 'mvn.cmd' : 'mvn'

// Generate a settings.xml pointing to the IT local repo
def localRepo = new File(basedir.parentFile.parentFile, 'local-repo')
def settingsFile = new File(basedir, 'settings.xml')
settingsFile.text = "<settings><localRepository>${localRepo.absolutePath}</localRepository></settings>"
def settingsArg = ['-s', settingsFile.absolutePath]

def cmd = [mvnCmd] + settingsArg + ['jackknife:index', '-B']
println "Prebuild: running ${cmd.join(' ')}"

def proc = new ProcessBuilder(cmd)
    .directory(basedir)
    .redirectErrorStream(true)
    .start()

proc.inputStream.eachLine { println "  [prebuild] ${it}" }
def exitCode = proc.waitFor()
assert exitCode == 0 : "Prebuild jackknife:index failed with exit code ${exitCode}"

// Record manifest timestamps for the verify script
def manifestDir = new File(basedir, '.jackknife/manifest')
assert manifestDir.exists() : 'Manifest dir should exist after prebuild'

def timestamps = new File(basedir, '.manifest-timestamps.txt')
timestamps.text = ''
manifestDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name.endsWith('.manifest')) {
        timestamps.append("${f.absolutePath}=${f.lastModified()}\n")
    }
}
println "Recorded ${timestamps.readLines().size()} manifest timestamps"
