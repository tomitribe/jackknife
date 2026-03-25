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

// Verify that second run of jackknife:index skipped manifests (up to date)

// -- Check build.log for skip message --
def buildLog = new File(basedir, 'build.log')
assert buildLog.exists() : 'build.log should exist'

def logContent = buildLog.text
// On second run, all jars should be skipped because manifests are up to date
assert logContent.contains('skipped') : 'Second run should report skipped jars'

// -- Verify manifest timestamps didn't change --
def timestampsFile = new File(basedir, '.manifest-timestamps.txt')
if (timestampsFile.exists()) {
    def lines = timestampsFile.readLines().findAll { it.trim() }
    for (line in lines) {
        def parts = line.split('=', 2)
        def file = new File(parts[0])
        def oldTimestamp = parts[1] as long
        assert file.exists() : "Manifest ${file.name} should still exist"
        assert file.lastModified() == oldTimestamp : "Manifest ${file.name} should not be re-written (timestamp unchanged)"
    }
    println "All ${lines.size()} manifest timestamps unchanged — skip-on-rerun verified"
}

// -- Verify USAGE.md still exists (regenerated each time) --
def usageMd = new File(basedir, '.jackknife/USAGE.md')
assert usageMd.exists() : 'USAGE.md should exist'
