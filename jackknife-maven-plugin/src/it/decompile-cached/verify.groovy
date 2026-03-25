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

// Verify that second decompile uses cached source (no re-decompile)

def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text

// On second run, DecompileMojo should find cached source and print "Source available"
assert logContent.contains('Source available') : 'Second decompile should use cached source ("Source available" message)'

// Filter out prebuild lines and check the main build didn't re-decompile
def mainBuildLines = logContent.readLines().findAll { !it.contains('[prebuild]') }
def mainBuildOutput = mainBuildLines.join('\n')
assert !mainBuildOutput.contains('Decompiling ') : 'Main build should NOT re-decompile (only prebuild should)'

// Source files should still exist
def sourceDir = new File(basedir, '.jackknife/source')
assert sourceDir.exists() : 'Source directory should exist'

// Find the DebugHandler source
def found = false
sourceDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name == 'DebugHandler.java') {
        found = true
        def content = f.text
        assert content.contains('class DebugHandler') : 'Cached source should be readable'
    }
}
assert found : 'DebugHandler.java should exist in cached source'
