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

// Verify decompile of a compile-scope dependency in a multi-module
// reactor produces source for Join at the reactor root.

// -- .jackknife/ at reactor root, NOT in child modules --
def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife directory should exist at reactor root'

def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

def sourceDir = new File(jackknife, 'source')
assert sourceDir.exists() : 'source directory should exist'

// -- Source directory for the jar --
def groupDir = new File(sourceDir, 'org.tomitribe')
assert groupDir.exists() : 'groupId source directory should exist for org.tomitribe'

def jarDirs = groupDir.listFiles()?.findAll { it.isDirectory() && it.name.contains('tomitribe-util') }
assert jarDirs != null && jarDirs.size() > 0 : 'Should have decompiled jar directory for tomitribe-util'

def jarDir = jarDirs[0]

// -- Join.java exists with correct package structure --
def joinFile = new File(jarDir, 'org/tomitribe/util/Join.java')
assert joinFile.exists() : 'Join.java should exist'

def content = joinFile.text
assert content.contains('class Join') : 'Should contain class declaration'

// -- Package structure matches --
assert joinFile.parentFile.name == 'util' : 'Package structure should match'
assert joinFile.parentFile.parentFile.name == 'tomitribe' : 'Package structure should match'

// -- At least 4 .java files decompiled --
def javaFiles = []
jarDir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
    if (f.name.endsWith('.java')) javaFiles.add(f)
}
assert javaFiles.size() >= 4 : "Should have at least 4 .java files (got ${javaFiles.size()})"

println "multi-decompile-compile-dependency verified:"
println "  .jackknife/source/ at reactor root: yes"
println "  .jackknife/ absent from modules: yes"
println "  org.tomitribe groupId dir: yes"
println "  Join.java found: yes"
println "  class Join declaration: yes"
println "  At least 4 .java files: yes (${javaFiles.size()})"
