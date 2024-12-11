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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.jarsigner.TsaSelector.TsaServer;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.apache.maven.shared.jarsigner.JarSignerUtil;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.apache.maven.toolchain.ToolchainManager;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Signs a project artifact and attachments using jarsigner.
 *
 * @author <a href="cs@schulte.it">Christian Schulte</a>
 * @since 1.0
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
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
     * <p>URL(s) to Time Stamping Authority (TSA) server(s) to use to timestamp the signing.
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * Separate multiple TSA URLs with comma (without space) or a nested XML tag.</p>
     *
     * <pre>{@code
     * <configuration>
     *   <tsa>http://timestamp.digicert.com,http://timestamp.globalsign.com/tsa/r6advanced1</tsa>
     * </configuration>
     * }</pre>
     *
     * <pre>{@code
     * <configuration>
     *   <tsa>
     *     <url>http://timestamp.digicert.com</url>
     *     <url>http://timestamp.globalsign.com/tsa/r6advanced1</url>
     *   </tsa>
     * </configuration>
     * }</pre>
     *
     * <p>Usage of multiple TSA servers only makes sense when {@link #maxTries} is more than 1. A different TSA server
     * will only be used at retries.</p>
     *
     * <p>Changed to a list since 3.1.0. Single XML element (without comma) is still supported.</p>
     *
     * @since 1.3
     */
    @Parameter(property = "jarsigner.tsa")
    private String[] tsa;

    /**
     * <p>Alias(es) for certificate(s) in the active keystore used to find a TSA URL. From the certificate the X509v3
     * extension "Subject Information Access" field is examined to find the TSA server URL. See
     * <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * Separate multiple aliases with comma (without space) or a nested XML tag.</p>
     *
     * <pre>{@code
     * <configuration>
     *   <tsacert>alias1,alias2</tsacert>
     * </configuration>
     * }</pre>
     *
     * <pre>{@code
     * <configuration>
     *   <tsacert>
     *     <alias>alias1</alias>
     *     <alias>alias2</alias>
     *   </tsacert>
     * </configuration>
     * }</pre>
     *
     * <p>Should not be used at the same time as the {@link #tsa} parameter (because jarsigner will typically ignore
     * tsacert, if tsa is set).</p>
     *
     * <p>Usage of multiple aliases only makes sense when {@link #maxTries} is more than 1. A different TSA server
     * will only be used at retries.</p>
     *
     * <p>Changed to a list since 3.1.0. Single XML element (without comma) is still supported.</p>
     *
     * @since 1.3
     */
    @Parameter(property = "jarsigner.tsacert")
    private String[] tsacert;

    /**
     * <p>OID(s) to send to the TSA server to identify the policy ID the server should use. If not specified TSA server
     * will choose a default policy ID. Each TSA server vendor will typically define their own policy OIDs. See
     * <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/windows/jarsigner.html#CCHIFIAD">options</a>.
     * Separate multiple OIDs with comma (without space) or a nested XML tag.</p>
     *
     * <pre>{@code
     * <configuration>
     *   <tsapolicyid>1.3.6.1.4.1.4146.2.3.1.2,2.16.840.1.114412.7.1</tsapolicyid>
     * </configuration>
     * }</pre>
     *
     * <pre>{@code
     * <configuration>
     *   <tsapolicyid>
     *     <oid>1.3.6.1.4.1.4146.2.3.1.2</oid>
     *     <oid>2.16.840.1.114412.7.1</oid>
     *   </tsapolicyid>
     * </configuration>
     * }</pre>
     *
     * <p>If used, the number of OIDs should be the same as the number of elements in {@link #tsa} or {@link #tsacert}.
     * The first OID will be used for the first TSA server, the second OID for the second TSA server and so on.</p>
     *
     * @since 3.1.0
     */
    @Parameter(property = "jarsigner.tsapolicyid")
    private String[] tsapolicyid;

    /**
     * The message digest algorithm to use in the messageImprint that the TSA server will timestamp. A default value
     * (for example {@code SHA-384}) will be selected by jarsigner if this parameter is not set. Only available in
     * Java 11 and later. See <a href="https://docs.oracle.com/en/java/javase/11/tools/jarsigner.html">options</a>.
     *
     * @since 3.1.0
     */
    @Parameter(property = "jarsigner.tsadigestalg")
    private String tsadigestalg;

    /**
     * Location of the extra certificate chain file. See
     * <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     *
     * @since 1.5
     */
    @Parameter(property = "jarsigner.certchain", required = false)
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

    /**
     * Maximum number of parallel threads to use when signing jar files. Increases performance when signing multiple jar
     * files, especially when network operations are used during signing, for example when using a Time Stamp Authority
     * or network based PKCS11 HSM solution for storing code signing keys. Note: the logging from the signing process
     * will be interleaved, and harder to read, when using many threads.
     *
     * @since 3.1.0
     */
    @Parameter(property = "jarsigner.threadCount", defaultValue = "1")
    private int threadCount;

    /** Current WaitStrategy, to allow for sleeping after a signing failure. */
    private WaitStrategy waitStrategy = this::defaultWaitStrategy;

    private TsaSelector tsaSelector;

    /** Exponent limit for exponential wait after failure function. 2^20 = 1048576 sec ~= 12 days. */
    private static final int MAX_WAIT_EXPONENT_ATTEMPT = 20;

    @Inject
    public JarsignerSignMojo(
            JarSigner jarSigner,
            ToolchainManager toolchainManager,
            @Named("mng-4384") SecDispatcher securityDispatcher) {
        super(jarSigner, toolchainManager, securityDispatcher);
    }

    // for testing; invoked via reflection
    JarsignerSignMojo() {
        super(null, null, null);
    }

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

        if (threadCount < 1) {
            getLog().warn(getMessage("invalidThreadCount", threadCount));
            threadCount = 1;
        }

        if (tsa.length > 0 && tsacert.length > 0) {
            getLog().warn(getMessage("warnUsageTsaAndTsacertSimultaneous"));
        }
        if (tsapolicyid.length > tsa.length || tsapolicyid.length > tsacert.length) {
            getLog().warn(getMessage("warnUsageTsapolicyidTooMany", tsapolicyid.length, tsa.length, tsacert.length));
        }
        if (tsa.length > 1 && maxTries == 1) {
            getLog().warn(getMessage("warnUsageMultiTsaWithoutRetry", tsa.length));
        }
        if (tsacert.length > 1 && maxTries == 1) {
            getLog().warn(getMessage("warnUsageMultiTsacertWithoutRetry", tsacert.length));
        }
        tsaSelector = new TsaSelector(tsa, tsacert, tsapolicyid, tsadigestalg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JarSignerRequest createRequest(File archive) throws MojoExecutionException {
        JarSignerSignRequest request = new JarSignerSignRequest();
        request.setSigfile(sigfile);
        updateJarSignerRequestWithTsa(request, tsaSelector.getServer());
        request.setCertchain(certchain);

        // Special handling for passwords through the Maven Security Dispatcher
        request.setKeypass(decrypt(keypass));
        return request;
    }

    /** Modifies JarSignerRequest with TSA parameters */
    private void updateJarSignerRequestWithTsa(JarSignerSignRequest request, TsaServer tsaServer) {
        request.setTsaLocation(tsaServer.getTsaUrl());
        request.setTsaAlias(tsaServer.getTsaAlias());
        request.setTsapolicyid(tsaServer.getTsaPolicyId());
        request.setTsadigestalg(tsaServer.getTsaDigestAlg());
    }

    /**
     * {@inheritDoc} Processing of files may be parallelized for increased performance.
     */
    @Override
    protected void processArchives(List<File> archives) throws MojoExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = archives.stream()
                .map(file -> executor.submit((Callable<Void>) () -> {
                    processArchive(file);
                    return null; // Return dummy value to conform with Void type
                }))
                .collect(Collectors.toList());
        try {
            for (Future<Void> future : futures) {
                future.get(); // Wait for completion. Result ignored, but may raise any Exception
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Thread interrupted while waiting for jarsigner to complete", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof MojoExecutionException) {
                throw (MojoExecutionException) e.getCause();
            }
            throw new MojoExecutionException("Error processing archives", e);
        } finally {
            // Shutdown of thread pool. If an Exception occurred, remaining threads will be aborted "best effort"
            executor.shutdownNow();
        }
    }

    /**
     * {@inheritDoc}
     *
     * Will retry signing up to maxTries times if it fails.
     *
     * @throws MojoExecutionException if all signing attempts fail
     */
    @Override
    protected void executeJarSigner(JarSigner jarSigner, JarSignerRequest request)
            throws JavaToolException, MojoExecutionException {
        for (int attempt = 0; attempt < maxTries; attempt++) {
            JavaToolResult result = jarSigner.execute(request);
            int resultCode = result.getExitCode();
            if (resultCode == 0) {
                return;
            }
            tsaSelector.registerFailure(); // Could be TSA server problem or something unrelated to TSA

            if (attempt < maxTries - 1) { // If not last attempt
                waitStrategy.waitAfterFailure(attempt, Duration.ofSeconds(maxRetryDelaySeconds));
                updateJarSignerRequestWithTsa((JarSignerSignRequest) request, tsaSelector.getServer());
            } else {
                // Last attempt failed, use this failure as resulting failure
                throw new MojoExecutionException(
                        getMessage("failure", getCommandlineInfo(result.getCommandline()), resultCode));
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
