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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_ERROR;
import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_OK;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JarsignerSignMojoTsaTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Locale originalLocale;
    private MavenProject project = mock(MavenProject.class);
    private JarSigner jarSigner = mock(JarSigner.class);

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
    public void testAllTsaParameters() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("archiveDirectory", createArchives(2).getPath());
        configuration.put("tsa", "http://my-timestamp.server.com");
        configuration.put("tsacert", "mytsacertalias"); // Normally you would not set both "tsacert alias" and "tsa url"
        configuration.put("tsapolicyid", "0.1.2.3.4");
        configuration.put("tsadigestalg", "SHA-384");

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        ArgumentCaptor<JarSignerSignRequest> requestArgument = ArgumentCaptor.forClass(JarSignerSignRequest.class);
        verify(jarSigner, times(3)).execute(requestArgument.capture());
        List<JarSignerSignRequest> requests = requestArgument.getAllValues();
        assertThat(requests, everyItem(RequestMatchers.hasTsa("http://my-timestamp.server.com")));
        assertThat(requests, everyItem(RequestMatchers.hasTsacert("mytsacertalias")));
        assertThat(requests, everyItem(RequestMatchers.hasTsaPolicyid("0.1.2.3.4")));
        assertThat(requests, everyItem(RequestMatchers.hasTsaDigestalg("SHA-384")));
    }

    @Test
    public void testMutipleTsa() throws Exception {
        // Special setup (non-normal) Mockito, because the same JarSignerSignRequest instance is used with modified URL
        List<String> tsaUrls = new ArrayList<>();
        when(jarSigner.execute(any(JarSignerSignRequest.class)))
                .thenAnswer(invocation -> {
                    JarSignerSignRequest request =
                            (JarSignerSignRequest) invocation.getArguments()[0];
                    tsaUrls.add(request.getTsaLocation());
                    return RESULT_ERROR;
                })
                .thenAnswer(invocation -> {
                    JarSignerSignRequest request =
                            (JarSignerSignRequest) invocation.getArguments()[0];
                    tsaUrls.add(request.getTsaLocation());
                    return RESULT_OK;
                });

        configuration.put("maxTries", "10");
        configuration.put("tsa", "http://my-timestamp.server.com,http://other-timestamp.example.com");

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner, times(2)).execute(any());
        assertEquals("http://my-timestamp.server.com", tsaUrls.get(0));
        assertEquals("http://other-timestamp.example.com", tsaUrls.get(1));
    }

    @Test
    public void testVerifyUsageOfBothTsaAndTsacert() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("maxTries", "2");
        configuration.put("tsa", "http://my-timestamp.server.com");
        configuration.put("tsacert", "mytsacertalias");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).warn(contains("Usage of both -tsa and -tsacert is undefined"));
    }

    @Test
    public void testVerifyUsageOfDifferentNumberOfTsapolicyidAndTsa() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("maxTries", "2");
        configuration.put("tsa", "http://my-timestamp1.server.com,http://my-timestamp2.server.com");
        configuration.put("tsapolicyid", "1.1,1.2,1.3"); // Too many OIDs specified
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).warn(contains("Too many (3) number of OIDs given"));
    }

    @Test
    public void testVerifyUsageOfDifferentNumberOfTsapolicyidAndTsacert() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("maxTries", "2");
        configuration.put("tsacert", "alias1,alias2");
        configuration.put("tsapolicyid", "1.1,1.2,1.3"); // Too many OIDs specified
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        // Alomst the same warning log as previous test case
        verify(log).warn(contains("Too many (3) number of OIDs given"));
    }

    @Test
    public void testVerifyMultipleTsaButNoRetry() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("tsa", "http://my-timestamp1.server.com,http://my-timestamp2.server.com");
        configuration.put("maxTries", "1");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).warn(contains("2 TSA URLs specified. Only first will be used because maxTries is set to 1"));
    }

    @Test
    public void testVerifyMultipleTsacertButNoRetry() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("tsacert", "alias1,alias2");
        configuration.put("maxTries", "1");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).warn(contains("2 TSA certificate aliases specified. Only first"));
    }

    private File createArchives(int numberOfArchives) throws IOException {
        File archiveDirectory = new File(projectDir, "my_archive_dir");
        archiveDirectory.mkdir();
        for (int i = 0; i < numberOfArchives; i++) {
            TestArtifacts.createDummyZipFile(new File(archiveDirectory, "archive" + i + ".jar"));
        }
        return archiveDirectory;
    }
}
