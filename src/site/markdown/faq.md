---
title: Frequently Asked Questions
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<a id="top"></a>

# Frequently Asked Questions

1. [What is Jarsigner?](#about)
1. [Is it possible to sign a single archive file?](#single-archive)
1. [How can I unsign JARs before re-signing them with my key?](#unsign)
1. [Why if I want to sign an artifact and then assembly there is some problem under windows?](#sign_and_assembly)

<a id="about"></a>

### What is Jarsigner?

You can read more about this tool in the offical guide:
[jarsigner - JAR Signing and Verification Tool](https://docs.oracle.com/javase/7/docs/technotes/tools/solaris/jarsigner.html).

<a id="single-archive"></a>

### Is it possible to sign a single archive file?

Signing or verifying a Java archive which is neither a project artifact nor an
attached artifact can be done by using the `archive` parameter of the
[`sign`](sign-mojo.html) and [`verify`](verify-mojo.html) goals. If this parameter is
set, the goals will process the specified archive and will not process any project
artifacts.

<a id="unsign"></a>

### How can I unsign JARs before re-signing them with my key?

To remove any existing signatures from the JARs before signing with your own key,
simply set the parameter
[`removeExistingSignatures`](sign-mojo.html#removeExistingSignatures) of the
[`sign`](sign-mojo.html) mojo to `true`. The resulting JAR will then appear to be
signed exactly once.

<a id="sign_and_assembly"></a>

### Why if I want to sign an artifact and then assembly there is some problem under windows?

To fix the problem, just move the assembly execution so it comes **after** the
jarsigner execution in the pom.

The whole story of the problem can be found in
[MJARSIGNER-13](https://issues.apache.org/jira/browse/MJARSIGNER-13) issue.
