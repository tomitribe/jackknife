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

// Verify IndexMojo generated manifests correctly

def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife directory should exist'

// -- Manifest directory structure --
def manifestDir = new File(jackknife, 'manifest')
assert manifestDir.exists() : 'manifest directory should exist'

// -- Flat groupId directories --
def groupDir = new File(manifestDir, 'org.tomitribe.jackknife')
assert groupDir.exists() : 'groupId directory should exist as flat name'

// -- Manifest file per jar --
def manifests = groupDir.listFiles().findAll { it.name.endsWith('.manifest') }
assert manifests.size() > 0 : 'Should have at least one manifest file'

def runtimeManifest = manifests.find { it.name.contains('jackknife-runtime') }
assert runtimeManifest != null : 'Should have manifest for jackknife-runtime'

// -- Manifest contains class names in dotted format --
def content = runtimeManifest.text
assert content.contains('org.tomitribe.jackknife.runtime.DebugHandler') : 'Should contain DebugHandler class'
assert content.contains('org.tomitribe.jackknife.runtime.TimingHandler') : 'Should contain TimingHandler class'
assert content.contains('org.tomitribe.jackknife.runtime.ProceedHandler') : 'Should contain ProceedHandler class'
assert content.contains('org.tomitribe.jackknife.runtime.HandlerRegistry') : 'Should contain HandlerRegistry class'

// -- Skips module-info.class and package-info.class --
assert !content.contains('module-info') : 'Should skip module-info.class'
assert !content.contains('package-info') : 'Should skip package-info.class'

// -- Resources listed under # resources header --
assert content.contains('# resources') : 'Should have resources header'
assert content.contains('META-INF/MANIFEST.MF') : 'Should list META-INF/MANIFEST.MF as resource'

// -- USAGE.md generated --
def usageMd = new File(jackknife, 'USAGE.md')
assert usageMd.exists() : 'USAGE.md should be generated'
assert usageMd.text.contains('Jackknife Usage') : 'USAGE.md should have usage content'
