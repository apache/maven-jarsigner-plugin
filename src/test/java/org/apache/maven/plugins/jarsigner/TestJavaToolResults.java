/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.jarsigner;

import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.apache.maven.shared.utils.cli.shell.Shell;

/**
 * Results from an invocation to {@link org.apache.maven.shared.utils.cli.javatool.JavaTool} to be used in tests.
 */
class TestJavaToolResults {
    static final JavaToolResult RESULT_OK = createOk();
    static final JavaToolResult RESULT_ERROR = createError();

    private static JavaToolResult createOk() {
        JavaToolResult result = new JavaToolResult();
        result.setExitCode(0);
        result.setExecutionException(null);
        result.setCommandline(getSimpleCommandline());
        return result;
    }

    private static JavaToolResult createError() {
        JavaToolResult result = new JavaToolResult();
        result.setExitCode(1);
        result.setExecutionException(null);
        result.setCommandline(getSimpleCommandline());
        return result;
    }

    private static Commandline getSimpleCommandline() {
        Shell shell = new Shell();
        Commandline commandline = new Commandline(shell);
        commandline.setExecutable("jarsigner");
        commandline.addArguments("my-project.jar", "myalias");
        return commandline;
    }
}
