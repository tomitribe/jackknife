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

// Create instrument config for a project class (src/main).
// In real usage, `mvn jackknife:instrument -Dclass=com.example.Helper -Dmethod=format`
// would detect it's project code and write to _project/.

def projectDir = new File(basedir, '.jackknife/instrument/_project')
projectDir.mkdirs()

def propsFile = new File(projectDir, 'project.properties')
propsFile.text = """# Jackknife instrumentation config
# Mode: debug

@com.example.Helper.format(java.lang.String) = com.example.Helper.format(java.lang.String)
@com.example.Helper.greet(java.lang.String) = com.example.Helper.greet(java.lang.String)
"""

println "Created project instrument config: ${propsFile}"
