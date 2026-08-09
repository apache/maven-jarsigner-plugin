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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.plugins.jarsigner.TsaSelector.TsaServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TsaSelectorTest {
    private static final String[] EMPTY = new String[0];
    private TsaSelector tsaSelector;
    private TsaServer tsaServer;
    private ExecutorService executor;

    @Test
    public void testNullInit() {
        tsaSelector = new TsaSelector(EMPTY, EMPTY, EMPTY, null);
        tsaServer = tsaSelector.getServer();
        assertNull(tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertNull(tsaServer.getTsaDigestAlg());

        // Make sure "next" server also contains null values
        tsaServer = tsaSelector.getServer();
        assertNull(tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertNull(tsaServer.getTsaDigestAlg());
    }

    @Test
    public void testFailureCount() {
        tsaSelector = new TsaSelector(
                new String[] {"http://url1.com", "http://url2.com", "http://url3.com"}, EMPTY, EMPTY, null);

        tsaServer = tsaSelector.getServer();
        assertEquals("http://url1.com", tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertNull(tsaServer.getTsaDigestAlg());

        tsaSelector.registerFailure();

        tsaServer = tsaSelector.getServer();
        assertEquals("http://url2.com", tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertNull(tsaServer.getTsaDigestAlg());

        // Should get same server again
        tsaServer = tsaSelector.getServer();
        assertEquals("http://url2.com", tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertNull(tsaServer.getTsaDigestAlg());
    }

    @Test
    @Timeout(30)
    public void testMultiThreadedScenario() throws InterruptedException {
        executor = Executors.newFixedThreadPool(2);

        tsaSelector = new TsaSelector(
                new String[] {"http://url1.com", "http://url2.com", "http://url3.com"}, EMPTY, EMPTY, null);

        // Register a single failure on the first URL so that the threads will use URL 2
        TsaServer serverThreadMain = tsaSelector.getServer();
        tsaSelector.registerFailure();

        CountDownLatch doneSignal = new CountDownLatch(2); // Indication that both threads has gotten a server
        Semaphore semaphore = new Semaphore(0); // When the threads may continue executing after gotten a server

        AtomicReference<TsaServer> serverThread1 = new AtomicReference<>();
        AtomicReference<TsaServer> serverThread2 = new AtomicReference<>();
        executor.submit(() -> {
            serverThread1.set(tsaSelector.getServer());
            doneSignal.countDown();
            semaphore.acquireUninterruptibly();
            tsaSelector.registerFailure();
        });
        executor.submit(() -> {
            serverThread2.set(tsaSelector.getServer());
            doneSignal.countDown();
            semaphore.acquireUninterruptibly();
            tsaSelector.registerFailure();
        });

        doneSignal.await(); // Wait until both threads has gotten an TsaServer
        semaphore.release(2); // Release both threads waiting for the semaphore

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals("http://url1.com", serverThreadMain.getTsaUrl());
        assertEquals("http://url2.com", serverThread1.get().getTsaUrl());
        assertEquals("http://url2.com", serverThread2.get().getTsaUrl());

        // The best URL is now number 3
        assertEquals("http://url3.com", tsaSelector.getServer().getTsaUrl());

        // Trigger a new failure, now URL 1 is best again.
        tsaSelector.registerFailure();
        assertEquals("http://url1.com", tsaSelector.getServer().getTsaUrl());
    }

    @Test
    public void testDigestAlgoritm() {
        tsaSelector = new TsaSelector(
                new String[] {"http://url1.com", "http://url2.com", "http://url3.com"}, EMPTY, EMPTY, "SHA-512");
        tsaServer = tsaSelector.getServer();
        assertEquals("http://url1.com", tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertEquals("SHA-512", tsaServer.getTsaDigestAlg());

        // Make sure that the next URL has the same digest algorithm
        tsaSelector.registerFailure();
        tsaServer = tsaSelector.getServer();
        assertEquals("http://url2.com", tsaServer.getTsaUrl());
        assertNull(tsaServer.getTsaAlias());
        assertNull(tsaServer.getTsaPolicyId());
        assertEquals("SHA-512", tsaServer.getTsaDigestAlg());
    }

    @Test
    public void testKeyStoreAliasAndOid() {
        tsaSelector = new TsaSelector(EMPTY, new String[] {"alias1", "alias2"}, new String[] {"1.1", "1.2"}, null);
        tsaServer = tsaSelector.getServer();
        assertNull(tsaServer.getTsaUrl());
        assertEquals("alias1", tsaServer.getTsaAlias());
        assertEquals("1.1", tsaServer.getTsaPolicyId());

        tsaSelector.registerFailure();
        tsaServer = tsaSelector.getServer();
        assertNull(tsaServer.getTsaUrl());
        assertEquals("alias2", tsaServer.getTsaAlias());
        assertEquals("1.2", tsaServer.getTsaPolicyId());
    }

    @Test
    public void testFailureRegistrationWithoutCurrent() {
        tsaSelector = new TsaSelector(
                new String[] {"http://url1.com"}, new String[] {"alias1"}, new String[] {"1.1"}, "SHA-384");
        tsaSelector.registerFailure(); // Should not throw any exception

        // Make sure further execution works
        tsaServer = tsaSelector.getServer();
        assertEquals("http://url1.com", tsaServer.getTsaUrl());
        assertEquals("alias1", tsaServer.getTsaAlias());
        assertEquals("1.1", tsaServer.getTsaPolicyId());
        assertEquals("SHA-384", tsaServer.getTsaDigestAlg());
    }
}
