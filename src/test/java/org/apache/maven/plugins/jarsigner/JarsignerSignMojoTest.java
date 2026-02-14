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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.apache.maven.shared.jarsigner.JarSignerUtil;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.hamcrest.MockitoHamcrest;

import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_ERROR;
import static org.apache.maven.plugins.jarsigner.TestJavaToolResults.RESULT_OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JarsignerSignMojoTest {

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
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    /** Standard Java project with nothing special configured */
    @Test
    public void testStandardJavaProject() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        ArgumentCaptor<JarSignerSignRequest> requestArgument = ArgumentCaptor.forClass(JarSignerSignRequest.class);
        verify(jarSigner).execute(requestArgument.capture());
        JarSignerSignRequest request = requestArgument.getValue();

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
        assertEquals(projectDir, request.getWorkingDirectory());
        assertEquals(mainArtifact.getFile(), request.getArchive());
        assertFalse(request.isProtectedAuthenticationPath());

        assertNull(request.getKeypass());
        assertNull(request.getSigfile());
        assertNull(request.getTsaLocation());
        assertNull(request.getTsaAlias());
        assertNull(request.getSignedjar()); // Current JarsignerSignMojo does not have support for this parameter.
        assertNull(request.getCertchain());
    }

    /** When jarsigner command invocation returns a non-zero exit code  */
    @Test
    public void testJarsignerNonZeroExitCode() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_ERROR);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        assertThat(mojoException.getMessage(), containsString(String.valueOf(RESULT_ERROR.getExitCode())));
        assertThat(
                mojoException.getMessage(),
                containsString(RESULT_ERROR.getCommandline().toString()));
    }

    /** When JavaTool throws an exception on execute() (when executing jarsigner). */
    @Test
    public void testJarsignerFailedToExecute() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenThrow(new JavaToolException("test failure"));
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        MojoExecutionException mojoException = assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
        assertThat(mojoException.getMessage(), containsString("test failure"));
    }

    /** Standard POM project with nothing special configured */
    @Test
    public void testStandardPOMProject() throws Exception {
        Artifact mainArtifact = TestArtifacts.createPomArtifact(projectDir, "my-project.pom");
        when(project.getArtifact()).thenReturn(mainArtifact);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner, never()).execute(any()); // Should not try to sign anything
    }

    /** Normal Java project, but avoid to process the main artifact (processMainArtifact to false) */
    @Test
    public void testDontProcessMainArtifact() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        configuration.put("processMainArtifact", "false");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner, never()).execute(any()); // Should not try to sign anything
    }

    /** Make sure that when skip is configured the Mojo will not process anything */
    @Test
    public void testSkip() throws Exception {
        when(project.getArtifact()).thenThrow(new AssertionError("Code should not try to get any artifacts"));
        configuration.put("skip", "true");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner, never()).execute(any()); // Should not try to sign anything
    }

    /** Only process the specified archive, don't process the main artifact nor the attached. */
    @Test
    public void testArchive() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        File archiveFile = TestArtifacts.createDummyZipFile(new File(projectDir, "archive.jar"));
        configuration.put("archive", archiveFile.getPath());
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        // Make sure only the jar pointed by "archive" has been processed, but not the main artifact
        verify(jarSigner, never()).execute(MockitoHamcrest.argThat(RequestMatchers.hasFileName("my-project.jar")));
        verify(jarSigner).execute(MockitoHamcrest.argThat(RequestMatchers.hasFileName("archive.jar")));
    }

    /** Test that it is possible to disable processing of attached artifacts */
    @Test
    public void testDontProcessAttachedArtifacts() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        configuration.put("processAttachedArtifacts", "false");

        when(project.getAttachedArtifacts())
                .thenReturn(Arrays.asList(TestArtifacts.createJarArtifact(
                        projectDir, "my-project-sources.jar", "sources", "java-source")));
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        // Make sure that only the main artifact has been processed, but not the attached artifact
        verify(jarSigner).execute(MockitoHamcrest.argThat(RequestMatchers.hasFileName("my-project.jar")));
        verify(jarSigner, never())
                .execute(MockitoHamcrest.argThat(RequestMatchers.hasFileName("my-project-sources.jar")));
    }

    /** A Java project with 3 types of artifacts: main, javadoc and sources */
    @Test
    public void testJavaProjectWithSourcesAndJavadoc() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(project.getAttachedArtifacts())
                .thenReturn(Arrays.asList(
                        TestArtifacts.createJarArtifact(projectDir, "my-project-sources.jar", "sources", "java-source"),
                        TestArtifacts.createJarArtifact(projectDir, "my-project-javadoc.jar", "javadoc", "javadoc")));
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        ArgumentCaptor<JarSignerSignRequest> requestArgument = ArgumentCaptor.forClass(JarSignerSignRequest.class);
        verify(jarSigner, times(3)).execute(requestArgument.capture());

        List<JarSignerSignRequest> requests = requestArgument.getAllValues();
        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project.jar")));
        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project-sources.jar")));
        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project-javadoc.jar")));
    }

    /**
     * Set most possible documented parameter (that does not interfere too much with testing of other parameters). See
     * Optional Parameters in site documentation.
     */
    @Test
    public void testBig() throws Exception {
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);

        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);

        when(project.getAttachedArtifacts())
                .thenReturn(Arrays.asList(
                        TestArtifacts.createJarArtifact(projectDir, "my-project-sources.jar", "sources"),
                        TestArtifacts.createJarArtifact(projectDir, "my-project-javadoc.jar", "javadoc"),
                        TestArtifacts.createJarArtifact(
                                projectDir, "my-project-included_and_excluded.jar", "included_and_excluded"),
                        TestArtifacts.createJarArtifact(
                                projectDir, "my-project-excluded_classifier.jar", "excluded_classifier")));

        File workingDirectory = new File(projectDir, "my_working_dir");
        workingDirectory.mkdir();

        File archiveDirectory = new File(projectDir, "my_archive_dir");
        archiveDirectory.mkdir();
        TestArtifacts.createDummyZipFile(new File(archiveDirectory, "archive1.jar"));
        File previouslySignedArchive =
                TestArtifacts.createDummySignedJarFile(new File(archiveDirectory, "previously_signed_archive.jar"));
        assertTrue(
                "previously_signed_archive.jar should be detected as a signed file before executing the Mojo",
                JarSignerUtil.isArchiveSigned(previouslySignedArchive));
        TestArtifacts.createDummyZipFile(new File(archiveDirectory, "archive_to_exclude.jar"));
        TestArtifacts.createDummyZipFile(new File(archiveDirectory, "not_this.par"));

        configuration.put("alias", "myalias");

        // Setting "archive" parameter disables effect of many others, so it is tested separately in other test case

        configuration.put("archiveDirectory", archiveDirectory.getPath());
        configuration.put("arguments", "jarsigner-arg1,jarsigner-arg2");
        configuration.put("certchain", "mycertchain");
        configuration.put("excludeClassifiers", "excluded_classifier,included_and_excluded");
        configuration.put("includeClassifiers", "sources,javadoc,included_and_excluded");
        configuration.put("includes", "*.jar");
        configuration.put("excludes", "*_to_exclude.jar");
        configuration.put("keypass", "mykeypass");
        configuration.put("keystore", "mykeystore");
        configuration.put("maxMemory", "mymaxmemory");
        configuration.put("processAttachedArtifacts", "true"); // Is default true, but set anyway.
        configuration.put("processMainArtifact", "true"); // Is default true, but set anyway.
        configuration.put("protectedAuthenticationPath", "true");
        configuration.put("providerArg", "myproviderarg");
        configuration.put("providerClass", "myproviderclass");
        configuration.put("providerName", "myprovidername");
        configuration.put("removeExistingSignatures", "true");
        configuration.put("sigfile", "mysigfile");

        configuration.put("skip", "false"); // Is default false, but set anyway
        configuration.put("storepass", "mystorepass");
        configuration.put("storetype", "mystoretype");
        configuration.put("tsa", "mytsa");
        configuration.put("tsacert", "mytsacert");
        configuration.put("verbose", "true");
        configuration.put("workingDirectory", workingDirectory.getPath());

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        ArgumentCaptor<JarSignerSignRequest> requestArgument = ArgumentCaptor.forClass(JarSignerSignRequest.class);
        verify(jarSigner, times(5)).execute(requestArgument.capture());
        List<JarSignerSignRequest> requests = requestArgument.getAllValues();

        assertThat(requests, everyItem(RequestMatchers.hasAlias("myalias")));

        assertThat(requests, hasItem(RequestMatchers.hasFileName("archive1.jar")));
        assertThat(requests, hasItem(RequestMatchers.hasFileName("previously_signed_archive.jar")));
        assertThat(requests, hasItem(not(RequestMatchers.hasFileName("archive_to_exclude.jar"))));
        assertThat(requests, hasItem(not(RequestMatchers.hasFileName("not_this.par"))));

        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project.jar")));
        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project-sources.jar")));
        assertThat(requests, hasItem(RequestMatchers.hasFileName("my-project-javadoc.jar")));
        assertThat(requests, hasItem(not(RequestMatchers.hasFileName("my-project-included_and_excluded.jar"))));
        assertThat(requests, hasItem(not(RequestMatchers.hasFileName("my-project-excluded_classifier.jar"))));

        assertThat(
                requests, everyItem(RequestMatchers.hasArguments(new String[] {"jarsigner-arg1", "jarsigner-arg2"})));
        assertThat(requests, everyItem(RequestMatchers.hasCertchain("mycertchain")));
        assertThat(requests, everyItem(RequestMatchers.hasKeypass("mykeypass")));
        assertThat(requests, everyItem(RequestMatchers.hasKeystore("mykeystore")));
        assertThat(requests, everyItem(RequestMatchers.hasMaxMemory("mymaxmemory")));
        assertThat(requests, everyItem(RequestMatchers.hasProtectedAuthenticationPath(true)));
        assertThat(requests, everyItem(RequestMatchers.hasProviderArg("myproviderarg")));
        assertThat(requests, everyItem(RequestMatchers.hasProviderClass("myproviderclass")));
        assertThat(requests, everyItem(RequestMatchers.hasProviderName("myprovidername")));
        assertFalse(JarSignerUtil.isArchiveSigned(previouslySignedArchive)); // Make sure previous signing is gone
        assertThat(requests, everyItem(RequestMatchers.hasSigfile("mysigfile")));
        assertThat(requests, everyItem(RequestMatchers.hasStorepass("mystorepass")));
        assertThat(requests, everyItem(RequestMatchers.hasStoretype("mystoretype")));
        assertThat(requests, everyItem(RequestMatchers.hasTsa("mytsa")));
        assertThat(requests, everyItem(RequestMatchers.hasTsacert("mytsacert")));
        assertThat(requests, everyItem(RequestMatchers.hasVerbose(true)));
    }

    /** Make sure that if a custom ToolchainManager is set on the Mojo, it is used by jarSigner */
    @Test
    public void testToolchainManager() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);

        Toolchain toolchain = mock(Toolchain.class);
        ToolchainManager toolchainManager = mock(ToolchainManager.class);
        when(toolchainManager.getToolchainFromBuildContext(any(), any())).thenReturn(toolchain);
        mojoTestCreator.setToolchainManager(toolchainManager);

        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner).setToolchain(toolchain);
    }

    /** Make sure the Mojo correctly invokes the SecDispatcher for decryption of passwords */
    @Test
    public void testSecurityDispatcher() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);

        configuration.put("keypass", "mykeypass_encrypted");
        configuration.put("storepass", "mystorepass_encrypted");

        mojoTestCreator.setSecDispatcher(str -> str.replace("_encrypted", "")); // "Decrypts" a password
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner).execute(MockitoHamcrest.argThat(RequestMatchers.hasKeypass("mykeypass")));
        verify(jarSigner).execute(MockitoHamcrest.argThat(RequestMatchers.hasStorepass("mystorepass")));
    }

    /** Make sure that a customer file encoding to jarsigner can be set and that it does not get duplicated */
    @Test
    public void testSetCustomFileEncoding() throws Exception {
        Artifact mainArtifact = TestArtifacts.createJarArtifact(projectDir, "my-project.jar");
        when(project.getArtifact()).thenReturn(mainArtifact);
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        configuration.put("arguments", "-J-Dfile.encoding=ISO-8859-1,argument2");
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(jarSigner)
                .execute(MockitoHamcrest.argThat(
                        RequestMatchers.hasArguments(new String[] {"-J-Dfile.encoding=ISO-8859-1", "argument2"})));
    }

    /**
     * Test what is logged when verbose=true. The sign-mojo.html documentation indicates that the verbose flag should
     * be sent in to the jarsigner command. That is true, but in addition to this it is also (undocumented) used to
     * control the level of some logging events.
     */
    @Test
    public void testLoggingVerboseTrue() throws Exception {
        when(log.isDebugEnabled()).thenReturn(true);
        Artifact mainArtifact = TestArtifacts.createPomArtifact(projectDir, "pom.xml");
        when(project.getArtifact()).thenReturn(mainArtifact);
        configuration.put("processAttachedArtifacts", "false");
        File archiveDirectory = new File(projectDir, "my_archive_dir");
        archiveDirectory.mkdir();
        TestArtifacts.createDummyZipFile(new File(archiveDirectory, "archive1.jar"));
        configuration.put("archiveDirectory", archiveDirectory.getPath());
        configuration.put("verbose", "true");
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).info(contains("Unsupported artifact "));
        verify(log).info(contains("Forcibly ignoring attached artifacts"));
        verify(log).info(contains("Processing "));
        verify(log).info(contains("1 archive(s) processed"));
    }

    /** Test what is logged when verbose=false */
    @Test
    public void testLoggingVerboseFalse() throws Exception {
        when(log.isDebugEnabled()).thenReturn(true);
        Artifact mainArtifact = TestArtifacts.createPomArtifact(projectDir, "pom.xml");
        when(project.getArtifact()).thenReturn(mainArtifact);
        configuration.put("processAttachedArtifacts", "false");
        File archiveDirectory = new File(projectDir, "my_archive_dir");
        archiveDirectory.mkdir();
        TestArtifacts.createDummyZipFile(new File(archiveDirectory, "archive1.jar"));
        configuration.put("archiveDirectory", archiveDirectory.getPath());
        configuration.put("verbose", "false");
        when(jarSigner.execute(any(JarSignerSignRequest.class))).thenReturn(RESULT_OK);
        JarsignerSignMojo mojo = mojoTestCreator.configure(configuration);

        mojo.execute();

        verify(log).debug(contains("Unsupported artifact "));
        verify(log).debug(contains("Forcibly ignoring attached artifacts"));
        verify(log).debug(contains("Processing "));
        verify(log).info(contains("1 archive(s) processed"));
    }
}
