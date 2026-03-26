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

// Verify that jackknife:index in a multi-module reactor creates manifests
// at the reactor root for a compile-scope dependency

def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife directory should exist at reactor root'

def manifestDir = new File(jackknife, 'manifest')
assert manifestDir.exists() : 'manifest directory should exist'

// -- Manifest for tomitribe-util --
def groupDir = new File(manifestDir, 'org.tomitribe')
assert groupDir.exists() : 'org.tomitribe groupId directory should exist'

def manifests = groupDir.listFiles().findAll { it.name.endsWith('.manifest') }
assert manifests.size() > 0 : 'Should have at least one manifest file'

def utilManifest = manifests.find { it.name.contains('tomitribe-util') }
assert utilManifest != null : 'Should have manifest for tomitribe-util'

// -- No .jackknife/ in child modules --
def moduleAJackknife = new File(basedir, 'module-a/.jackknife')
assert !moduleAJackknife.exists() : '.jackknife should NOT exist in module-a'

def moduleBJackknife = new File(basedir, 'module-b/.jackknife')
assert !moduleBJackknife.exists() : '.jackknife should NOT exist in module-b'

// -- USAGE.md generated --
def usageMd = new File(jackknife, 'USAGE.md')
assert usageMd.exists() : 'USAGE.md should be generated'

println "multi-index-compile-dependency verified:"
println "  .jackknife/ at reactor root: yes"
println "  .jackknife/ absent from modules: yes"
println "  tomitribe-util manifest exists: yes"
println "  USAGE.md generated: yes"
