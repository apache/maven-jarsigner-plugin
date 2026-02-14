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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.jarsigner.JarsignerSignMojo.Sleeper;
import org.apache.maven.plugins.jarsigner.JarsignerSignMojo.WaitStrategy;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_ERROR;
import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JarsignerSignMojoRetryTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Locale originalLocale;
    private MavenProject project = mock(MavenProject.class);
    private JarSigner jarSigner = mock(JarSigner.class);
    private WaitStrategy waitStrategy = mock(WaitStrategy.class);
    private File projectDir;
    private Map<String, String> configuration = new LinkedHashMap<>();
    private Log log;
    private MojoTestCreator<JarsignerSignMojo> mojoTestCreator;

    @Before
    public void setUp() throws Exception {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH); // For English ResourceBundle to test log messages
        projectDir = folder.newFolder("dummy-project");
        mojoTestCreator =
                new MojoTestCreator<JarsignerSignMojo>(JarsignerSignMojo.class, project, projectDir, jarSigner);
        log = mock(Log.class);
        mojoTestCreator.setLog(log);
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void testSignSuccessOnFirst() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("maxTries", "1");
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner)
                .execute(argThat(request -> request.getArchive().getPath().endsWith("my-project.jar")));
        verify(waitStrategy, never()).waitAfterFailure(0, Duration.ofSeconds(0));
    }

    @Test
    public void testSignFailureOnFirst() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_ERROR);
        configuration.put("maxTries", "1");
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);
        assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        verify(jarSigner).execute(any());
        verify(waitStrategy, never()).waitAfterFailure(0, Duration.ofSeconds(0));
    }

    @Test
    public void testSignFailureOnFirstSuccessOnSecond() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenReturn(RESULT_ERROR)
                .thenReturn(RESULT_OK);
        configuration.put("maxTries", "2");
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner, times(2)).execute(any());
        verify(waitStrategy).waitAfterFailure(0, Duration.ofSeconds(0));
    }

    @Test
    public void testSignFailureOnFirstFailureOnSecond() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenReturn(RESULT_ERROR)
                .thenReturn(RESULT_ERROR);
        configuration.put("maxTries", "2");
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);
        assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        verify(jarSigner, times(2)).execute(any());
        verify(waitStrategy).waitAfterFailure(0, Duration.ofSeconds(0));
    }

    @Test
    public void testInvalidMaxTriesZero() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_ERROR);

        configuration.put("maxTries", "0"); // Setting an "invalid" value
        mojoTestCreator.setWaitStrategy(waitStrategy);

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });

        verify(jarSigner).execute(any()); // Should have tried exactly one time, regardless of invalid value
        verify(log).warn(contains("Invalid maxTries"));
        verify(log).warn(contains("0"));
    }

    @Test
    public void testInvalidMaxTriesNegative() throws Exception {
        // Make result ok, to make this test check more things (compared to testInvalidMaxTries_zero())
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);

        configuration.put("maxTries", "-2"); // Setting an "invalid" value
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner).execute(any()); // Should have tried exactly one time, regardless of invalid value
        verify(log).warn(contains("Invalid maxTries"));
        verify(log).warn(contains("-2"));
    }

    @Test
    public void testMaxRetryDelaySeconds() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenReturn(RESULT_ERROR)
                .thenReturn(RESULT_ERROR)
                .thenReturn(RESULT_OK);

        configuration.put("maxTries", "3");
        configuration.put("maxRetryDelaySeconds", "30");
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(waitStrategy).waitAfterFailure(0, Duration.ofSeconds(30));
        verify(waitStrategy).waitAfterFailure(1, Duration.ofSeconds(30));
    }

    @Test
    public void testInvalidMaxRetryDelaySecondsNegative() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenReturn(RESULT_ERROR)
                .thenReturn(RESULT_OK);

        configuration.put("maxTries", "10");
        configuration.put("maxRetryDelaySeconds", "-5"); // Setting an "invalid" value
        mojoTestCreator.setWaitStrategy(waitStrategy);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(waitStrategy).waitAfterFailure(0, Duration.ofSeconds(0));
        verify(jarSigner, times(2)).execute(any());
        verify(log).warn(contains("Invalid maxRetryDelaySeconds"));
        verify(log).warn(contains("-5"));
    }

    @Test
    public void testDefaultWaitStrategy() throws Exception {
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        long noSleepValue = 42_001;
        // Storage of the most recent sleep value
        AtomicLong sleepValue = new AtomicLong(noSleepValue);
        Sleeper sleeper = value -> sleepValue.set(value);

        mojo.waitAfterFailure(0, Duration.ofSeconds(0), sleeper);
        assertEquals(noSleepValue, sleepValue.get());

        mojo.waitAfterFailure(1, Duration.ofSeconds(0), sleeper);
        assertEquals(noSleepValue, sleepValue.get());

        mojo.waitAfterFailure(0, Duration.ofSeconds(1), sleeper);
        assertEquals(1000, sleepValue.get());
        verify(log).info(contains("for 1 seconds"));

        mojo.waitAfterFailure(1, Duration.ofSeconds(1), sleeper);
        assertEquals(1000, sleepValue.get());
        mojo.waitAfterFailure(2, Duration.ofSeconds(1), sleeper);
        assertEquals(1000, sleepValue.get());

        mojo.waitAfterFailure(3, Duration.ofSeconds(100), sleeper);
        assertEquals(8000, sleepValue.get());

        mojo.waitAfterFailure(Integer.MAX_VALUE, Duration.ofSeconds(100), sleeper);
        assertEquals(100_000, sleepValue.get());

        sleepValue.set(noSleepValue); // "reset" sleep value
        mojo.waitAfterFailure(Integer.MIN_VALUE, Duration.ofSeconds(100), sleeper);
        // Make sure sleep has not been invoked, should be the "reset" value
        assertEquals(noSleepValue, sleepValue.get());

        // Testing the attempt limit used in exponential function. Will return a odd value (2^20).
        mojo.waitAfterFailure(10000, Duration.ofDays(356), sleeper);
        assertEquals(Duration.ofSeconds(1048576).toMillis(), sleepValue.get());
        // Testing of attempt limit, when using a smaller maxRetryDelay
        mojo.waitAfterFailure(10000, Duration.ofDays(1), sleeper);
        assertEquals(Duration.ofDays(1).toMillis(), sleepValue.get());

        Sleeper iterruptedSleeper = value -> {
            throw new InterruptedException("Thread was interrupted while sleeping.");
        };
        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.waitAfterFailure(0, Duration.ofSeconds(10), iterruptedSleeper);
        });
        assertThat(mojoException.getMessage(), containsString("interrupted while waiting after failure"));
    }

    /** Check that the error returned from a re-try scenario where all execution fails is the "correct" error */
    @Test
    public void testLastErrorReturned() throws Exception {
        JavaToolResult lastError = new JavaToolResult();
        lastError.setExitCode(42); // The exit code of the last jarsigner execution
        lastError.setExecutionException(null);
        lastError.setCommandline(RESULT_ERROR.getCommandline());

        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenReturn(RESULT_ERROR)
                .thenReturn(lastError);

        configuration.put("maxTries", "5");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });

        // Make sure that the last error exit code is present
        assertThat(mojoException.getMessage(), containsString(String.valueOf(42)));
        // Make sure that the first error exit code is not present
        assertThat(mojoException.getMessage(), not(containsString(String.valueOf(1))));
    }
}
