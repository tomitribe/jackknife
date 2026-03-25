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

// Pre-create .jackknife/instrument/ config to simulate InstrumentMojo output.
// This lets us test ProcessMojo in isolation.

def groupDir = new File(basedir, '.jackknife/instrument/org.tomitribe.jackknife')
groupDir.mkdirs()

// Find the actual jar file name from the local repo
def localRepo = new File(basedir, '../../local-repo')
def runtimeDir = new File(localRepo, 'org/tomitribe/jackknife/jackknife-runtime')
def versionDirs = runtimeDir.listFiles()?.findAll { it.isDirectory() }
def snapshotDir = versionDirs?.find { it.name.contains('SNAPSHOT') } ?: versionDirs?.first()
def jarFile = snapshotDir?.listFiles()?.find { it.name.endsWith('.jar') }
def jarName = jarFile?.name ?: 'jackknife-runtime-0.3-SNAPSHOT.jar'

def propsFile = new File(groupDir, "${jarName}.properties")
propsFile.text = """# Jackknife instrumentation config
# Mode: debug

@org.tomitribe.jackknife.runtime.HandlerRegistry.getHandler(java.lang.String,java.lang.String) = org.tomitribe.jackknife.runtime.HandlerRegistry.getHandler(java.lang.String,java.lang.String)
"""

println "Created instrument config: ${propsFile}"
println "Jar name: ${jarName}"
