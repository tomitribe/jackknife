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

// Verify DecompileMojo decompiled the jar correctly

def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife directory should exist'

def sourceDir = new File(jackknife, 'source')
assert sourceDir.exists() : 'source directory should exist'

// -- Source directory for the jar --
def groupDir = new File(sourceDir, 'org.tomitribe.jackknife')
assert groupDir.exists() : 'groupId source directory should exist'

def jarDirs = groupDir.listFiles()?.findAll { it.isDirectory() && it.name.contains('jackknife-runtime') }
assert jarDirs != null && jarDirs.size() > 0 : 'Should have decompiled jar directory'

def jarDir = jarDirs[0]

// -- Writes one .java file per class --
def javaFiles = []
jarDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name.endsWith('.java')) javaFiles.add(f)
}
assert javaFiles.size() >= 4 : "Should have at least 4 .java files (got ${javaFiles.size()})"

// -- Correct class returned (the requested class exists) --
def debugHandler = new File(jarDir, 'org/tomitribe/jackknife/runtime/DebugHandler.java')
assert debugHandler.exists() : 'DebugHandler.java should exist'

def content = debugHandler.text
assert content.contains('class DebugHandler') : 'Should contain class declaration'
assert content.contains('invoke') : 'Should contain invoke method'

// -- Other classes also decompiled (entire jar) --
def timingHandler = new File(jarDir, 'org/tomitribe/jackknife/runtime/TimingHandler.java')
assert timingHandler.exists() : 'TimingHandler.java should also be decompiled'

def proceedHandler = new File(jarDir, 'org/tomitribe/jackknife/runtime/ProceedHandler.java')
assert proceedHandler.exists() : 'ProceedHandler.java should also be decompiled'

def registry = new File(jarDir, 'org/tomitribe/jackknife/runtime/HandlerRegistry.java')
assert registry.exists() : 'HandlerRegistry.java should also be decompiled'

// -- Source directory structure matches package structure --
assert debugHandler.parentFile.name == 'runtime' : 'Package structure should match'
assert debugHandler.parentFile.parentFile.name == 'jackknife' : 'Package structure should match'
