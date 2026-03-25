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

// Verify ProcessMojo transformed the jar and moved the config

def jackknife = new File(basedir, '.jackknife')
assert jackknife.exists() : '.jackknife directory should exist'

def modifiedDir = new File(jackknife, 'modified')
assert modifiedDir.exists() : 'modified directory should exist'

def modGroupDir = new File(modifiedDir, 'org.tomitribe.jackknife')
assert modGroupDir.exists() : 'modified groupId directory should exist'

// -- Patched jar should exist --
def patchedJars = modGroupDir.listFiles()?.findAll { it.name.endsWith('.jar') }
assert patchedJars != null && patchedJars.size() > 0 : 'Should have patched jar in modified/'

def patchedJar = patchedJars[0]

// -- Properties receipt should be moved to modified/ --
def receipts = modGroupDir.listFiles()?.findAll { it.name.endsWith('.properties') }
assert receipts != null && receipts.size() > 0 : 'Should have properties receipt in modified/'

// -- Instrument dir should have the config file removed (moved to modified/) --
def instrumentGroupDir = new File(jackknife, 'instrument/org.tomitribe.jackknife')
def remainingProps = instrumentGroupDir.listFiles()?.findAll { it.name.endsWith('.properties') }
assert remainingProps == null || remainingProps.size() == 0 : 'Properties should be moved from instrument/ to modified/'

// -- Patched jar should contain handlers.properties --
def jar = new java.util.jar.JarFile(patchedJar)
def handlerConfig = jar.getEntry('META-INF/jackknife/handlers.properties')
assert handlerConfig != null : 'Patched jar should contain META-INF/jackknife/handlers.properties'

def configContent = jar.getInputStream(handlerConfig).text
assert configContent.contains('debug') : 'Handler config should contain debug mode'
assert configContent.contains('HandlerRegistry') : 'Handler config should reference HandlerRegistry'
assert configContent.contains('getHandler') : 'Handler config should reference getHandler method'

// -- Non-class entries should be preserved --
def manifest = jar.getEntry('META-INF/MANIFEST.MF')
assert manifest != null : 'Non-class entries (MANIFEST.MF) should be copied through'

// -- Enhanced class should exist --
def enhancedClass = jar.getEntry('org/tomitribe/jackknife/runtime/HandlerRegistry.class')
assert enhancedClass != null : 'Enhanced class should exist in patched jar'

jar.close()
