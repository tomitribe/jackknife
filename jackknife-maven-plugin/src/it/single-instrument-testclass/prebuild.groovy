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

// Create instrument config for a test class (src/test).
// In real usage: mvn jackknife:instrument -Dclass=com.example.TestHelper -Dmethod=compute

def projectDir = new File(basedir, '.jackknife/instrument/_project')
projectDir.mkdirs()

def propsFile = new File(projectDir, 'project.properties')
propsFile.text = """# Jackknife instrumentation config
# Mode: debug

@com.example.TestHelper.compute(java.lang.String) = com.example.TestHelper.compute(java.lang.String)
@com.example.TestHelper.format(java.lang.String) = com.example.TestHelper.format(java.lang.String)
"""

println "Created project instrument config for test class: ${propsFile}"
