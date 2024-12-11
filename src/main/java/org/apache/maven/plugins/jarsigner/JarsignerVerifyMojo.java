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

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerUtil;
import org.apache.maven.shared.jarsigner.JarSignerVerifyRequest;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.apache.maven.toolchain.ToolchainManager;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Checks the signatures of a project artifact and attachments using jarsigner.
 *
 * @author <a href="cs@schulte.it">Christian Schulte</a>
 * @since 1.0
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class JarsignerVerifyMojo extends AbstractJarsignerMojo {

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.certs", defaultValue = "false")
    private boolean certs;

    /**
     * When <code>true</code> this will make the execute() operation fail,
     * throwing an exception, when verifying an unsigned jar.
     * Primarily to keep backwards compatibility with existing code, and allow reusing the
     * mojo in unattended operations when set to <code>false</code>.
     *
     * @since 1.3
     **/
    @Parameter(property = "jarsigner.errorWhenNotSigned", defaultValue = "false")
    private boolean errorWhenNotSigned;

    @Inject
    public JarsignerVerifyMojo(
            JarSigner jarSigner,
            ToolchainManager toolchainManager,
            @Named("mng-4384") SecDispatcher securityDispatcher) {
        super(jarSigner, toolchainManager, securityDispatcher);
    }

    // for testing; invoked via reflection
    JarsignerVerifyMojo() {
        super(null, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JarSignerRequest createRequest(File archive) {
        JarSignerVerifyRequest request = new JarSignerVerifyRequest();
        request.setCerts(certs);
        return request;
    }

    @Override
    protected void preProcessArchive(File archive) throws MojoExecutionException {
        super.preProcessArchive(archive);

        if (errorWhenNotSigned) {

            // check archive if signed
            boolean archiveSigned;
            try {
                archiveSigned = JarSignerUtil.isArchiveSigned(archive);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to check if archive " + archive + " is signed: " + e.getMessage(), e);
            }

            if (!archiveSigned) {

                // fails, archive must be signed
                throw new MojoExecutionException(getMessage("archiveNotSigned", archive));
            }
        }
    }

    @Override
    protected void executeJarSigner(JarSigner jarSigner, JarSignerRequest request)
            throws JavaToolException, MojoExecutionException {
        JavaToolResult result = jarSigner.execute(request);
        int resultCode = result.getExitCode();
        if (resultCode != 0) {
            throw new MojoExecutionException(
                    getMessage("failure", getCommandlineInfo(result.getCommandline()), resultCode));
        }
    }
}
