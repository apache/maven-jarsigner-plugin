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

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.apache.maven.shared.jarsigner.JarSignerUtil;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;

/**
 * Signs a project artifact and attachments using jarsigner.
 *
 * @author <a href="cs@schulte.it">Christian Schulte</a>
 * @since 1.0
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.PACKAGE)
public class JarsignerSignMojo extends AbstractJarsignerMojo {

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.keypass")
    private String keypass;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.sigfile")
    private String sigfile;

    /**
     * Indicates whether existing signatures should be removed from the processed JAR files prior to signing them. If
     * enabled, the resulting JAR will appear as being signed only once.
     *
     * @since 1.1
     */
    @Parameter(property = "jarsigner.removeExistingSignatures", defaultValue = "false")
    private boolean removeExistingSignatures;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     *
     * @since 1.3
     */
    @Parameter(property = "jarsigner.tsa")
    private String tsa;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     *
     * @since 1.3
     */
    @Parameter(property = "jarsigner.tsacert")
    private String tsacert;

    /**
     * Location of the extra certchain file.
     * See
     * <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">
     *   Java SE 7 documentation
     * </a>
     * for more info.
     *
     * @since 1.5
     */
    @Parameter(property = "jarsigner.certchain", readonly = true, required = false)
    private File certchain;

    /**
     * How many times to try to sign a jar (assuming each previous attempt is a failure). This option may be desirable
     * if any network operations are used during signing, for example using a Time Stamp Authority or network based
     * PKCS11 HSM solution for storing code signing keys.
     *
     * The default value of 1 indicates that no retries should be made.
     *
     * @since 3.1.0
     */
    @Parameter(property = "jarsigner.maxTries", defaultValue = "1")
    private int maxTries;

    /**
     * Maximum delay, in seconds, to wait after a failed attempt before re-trying. The delay after a failed attempt
     * follows an exponential backoff strategy, with increasing delay times.
     *
     * @since 3.1.0
     */
    @Parameter(property = "jarsigner.maxRetryDelaySeconds", defaultValue = "0")
    private int maxRetryDelaySeconds;

    /** Current WaitStrategy, to allow for sleeping after a signing failure. */
    private WaitStrategy waitStrategy = this::defaultWaitStrategy;

    /** Exponent limit for exponential wait after failure function. 2^20 = 1048576 sec ~= 12 days. */
    private static final int MAX_WAIT_EXPONENT_ATTEMPT = 20;

    @Override
    protected String getCommandlineInfo(final Commandline commandLine) {
        String commandLineInfo = commandLine != null ? commandLine.toString() : null;

        if (commandLineInfo != null) {
            commandLineInfo = StringUtils.replace(commandLineInfo, this.keypass, "'*****'");
        }

        return commandLineInfo;
    }

    @Override
    protected void preProcessArchive(final File archive) throws MojoExecutionException {
        if (removeExistingSignatures) {
            try {
                JarSignerUtil.unsignArchive(archive);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to unsign archive " + archive + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected void validateParameters() throws MojoExecutionException {
        super.validateParameters();

        if (maxTries < 1) {
            getLog().warn(getMessage("invalidMaxTries", maxTries));
            maxTries = 1;
        }

        if (maxRetryDelaySeconds < 0) {
            getLog().warn(getMessage("invalidMaxRetryDelaySeconds", maxRetryDelaySeconds));
            maxRetryDelaySeconds = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JarSignerRequest createRequest(File archive) throws MojoExecutionException {
        JarSignerSignRequest request = new JarSignerSignRequest();
        request.setSigfile(sigfile);
        request.setTsaLocation(tsa);
        request.setTsaAlias(tsacert);
        request.setCertchain(certchain);

        // Special handling for passwords through the Maven Security Dispatcher
        request.setKeypass(decrypt(keypass));
        return request;
    }

    /**
     * {@inheritDoc}
     *
     * Will retry signing up to maxTries times if it fails.
     *
     * @throws MojoExecutionException If all signing attempts fail.
     */
    @Override
    protected void executeJarSigner(JarSigner jarSigner, JarSignerRequest request)
            throws JavaToolException, MojoExecutionException {
        for (int attempt = 0; attempt < maxTries; attempt++) {
            JavaToolResult result = jarSigner.execute(request);
            int resultCode = result.getExitCode();
            Commandline commandLine = result.getCommandline();
            if (resultCode == 0) {
                return;
            }
            if (attempt < maxTries - 1) { // If not last attempt
                waitStrategy.waitAfterFailure(attempt, Duration.ofSeconds(maxRetryDelaySeconds));
            } else {
                // Last attempt failed, use this failure as resulting failure
                throw new MojoExecutionException(getMessage("failure", getCommandlineInfo(commandLine), resultCode));
            }
        }
    }

    /** Set current WaitStrategy. Package private for testing. */
    void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    /** Wait/sleep after a signing failure before the next re-try should happen. */
    @FunctionalInterface
    interface WaitStrategy {
        /**
         * Will be called after a signing failure, if a re-try is about to happen. May as a side effect sleep current
         * thread for some time.
         *
         * @param attempt the attempt number (0 is the first)
         * @param maxRetryDelay the maximum duration to sleep (may be zero)
         * @throws MojoExecutionException if the sleep was interrupted
         */
        void waitAfterFailure(int attempt, Duration maxRetryDelay) throws MojoExecutionException;
    }

    private void defaultWaitStrategy(int attempt, Duration maxRetryDelay) throws MojoExecutionException {
        waitAfterFailure(attempt, maxRetryDelay, Thread::sleep);
    }

    /** Thread.sleep(long millis) interface to make testing easier */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Package private for testing */
    void waitAfterFailure(int attempt, Duration maxRetryDelay, Sleeper sleeper) throws MojoExecutionException {
        // Use attempt as exponent in the exponential function, but limit it to avoid too big values.
        int exponentAttempt = Math.min(attempt, MAX_WAIT_EXPONENT_ATTEMPT);
        long delayMillis = (long) (Duration.ofSeconds(1).toMillis() * Math.pow(2, exponentAttempt));
        delayMillis = Math.min(delayMillis, maxRetryDelay.toMillis());
        if (delayMillis > 0) {
            getLog().info("Sleeping after failed attempt for " + (delayMillis / 1000) + " seconds...");
            try {
                sleeper.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MojoExecutionException("Thread interrupted while waiting after failure", e);
            }
        }
    }
}
