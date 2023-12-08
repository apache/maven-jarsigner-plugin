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
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerVerifyRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.hamcrest.MockitoHamcrest;

import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_ERROR;
import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JarsignerVerifyMojoTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private MavenProject project = mock(MavenProject.class);
    private JarSigner jarSigner = mock(JarSigner.class);
    private File dummyMavenProjectDir;
    private Map<String, String> configuration = new LinkedHashMap<>();
    private Log log;
    private MojoTestCreator<JarsignerVerifyMojo> mojoTestCreator;

    @Before
    public void setUp() throws Exception {
        dummyMavenProjectDir = folder.newFolder("dummy-project");
        mojoTestCreator = new MojoTestCreator<JarsignerVerifyMojo>(
                JarsignerVerifyMojo.class, project, dummyMavenProjectDir, jarSigner);
        log = mock(Log.class);
        mojoTestCreator.setLog(log);
    }

    /** Standard Java project with nothing special configured */
    @Test
    public void testStandardJavaProject() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(dummyMavenProjectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerVerifyRequest.class))).thenReturn(RESULT_OK);
        JarsignerVerifyMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        ArgumentCaptor<JarSignerVerifyRequest> requestArgument = ArgumentCaptor.forClass(JarSignerVerifyRequest.class);
        verify(jarSigner).execute(requestArgument.capture());
        JarSignerVerifyRequest request = requestArgument.getValue();

        assertFalse(request.isVerbose());
        assertNull(request.getKeystore());
        assertNull(request.getStoretype());
        assertNull(request.getStorepass());
        assertNull(request.getAlias());
        assertNull(request.getProviderName());
        assertNull(request.getProviderClass());
        assertNull(request.getProviderArg());
        assertNull(request.getMaxMemory());
        assertThat(request.getArguments()[0], startsWith("-J-Dfile.encoding="));
        assertEquals(dummyMavenProjectDir, request.getWorkingDirectory());
        assertEquals(mainArtifact.getFile(), request.getArchive());
        assertFalse(request.isProtectedAuthenticationPath());

        assertFalse(request.isCerts()); // Only verify specific parameter
    }

    /** Invocing jarsigner with the -certs parameter */
    @Test
    public void testCertsTrue() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(dummyMavenProjectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerVerifyRequest.class))).thenReturn(RESULT_OK);
        configuration.put("certs", "true");
        JarsignerVerifyMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner).execute(argThat(request -> ((JarSignerVerifyRequest) request).isCerts()));
    }

    /** When the jarsigner signing verification check tells there is a problem with the signing of the file */
    @Test
    public void testVerifyFailure() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(dummyMavenProjectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerVerifyRequest.class))).thenReturn(RESULT_ERROR);
        JarsignerVerifyMojo mojo = mojoTestCreator.configure(configuration);

        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        assertThat(mojoException.getMessage(), containsString(String.valueOf(RESULT_ERROR.getExitCode())));
        assertThat(
                mojoException.getMessage(),
                containsString(RESULT_ERROR.getCommandline().toString()));
    }

    /** When setting errorWhenNotSigned, for file that has existing signing (should not fail) */
    @Test
    public void testErrorWhenNotSignedOnExistingSigning() throws Exception {
        File signedJar = TestArtifacts.createDummySignedJarFile(new File(dummyMavenProjectDir, "my-project.jar"));
        Artifact mainArtifact = TestArtifacts.createArtifact(signedJar);
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerVerifyRequest.class))).thenReturn(RESULT_OK);
        configuration.put("errorWhenNotSigned", "true");

        JarsignerVerifyMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner).execute(MockitoHamcrest.argThat(RequestMatchers.hasFileName("my-project.jar")));
    }

    /** When setting errorWhenNotSigned, for file that does not have existing signing (should fail) */
    @Test
    public void testErrorWhenNotSignedOnNonExistingSigning() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(dummyMavenProjectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerVerifyRequest.class))).thenReturn(RESULT_OK);
        configuration.put("errorWhenNotSigned", "true");

        JarsignerVerifyMojo mojo = mojoTestCreator.configure(configuration);

        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        assertThat(
                mojoException.getMessage(),
                containsString(mainArtifact.getFile().getPath()));
    }
}
