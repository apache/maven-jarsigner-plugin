package org.apache.maven.plugins.jarsigner;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolResult;
import org.junit.Test;
import org.mockito.Mockito;

public class AbstractJarsignerMojoTest {

    
    @Test
    public void testSignSuccessOnFirst() throws MojoExecutionException, JavaToolException {
        AbstractJarsignerMojo mojo = Mockito.mock(AbstractJarsignerMojo.class);
        JarSigner jarSigner = Mockito.mock( JarSigner.class );
        JarSignerRequest request = Mockito.mock( JarSignerRequest.class );
        Mockito.doCallRealMethod().when( mojo).sign( jarSigner,  request, 1 );
        JavaToolResult result = new JavaToolResult();
        result.setExitCode( 0 );
        Mockito.when( jarSigner.execute( request )).thenReturn( result );
        
        mojo.sign( jarSigner, request, 1 );
    }
    
    @Test(expected = MojoExecutionException.class)
    public void testSignFailureOnFirst() throws MojoExecutionException, JavaToolException {
        AbstractJarsignerMojo mojo = Mockito.mock(AbstractJarsignerMojo.class);
        JarSigner jarSigner = Mockito.mock( JarSigner.class );
        JarSignerRequest request = Mockito.mock( JarSignerRequest.class );
        Mockito.doCallRealMethod().when( mojo).sign( jarSigner,  request, 1 );
        JavaToolResult result = new JavaToolResult();
        result.setExitCode( 1 );
        Mockito.when( jarSigner.execute( request )).thenReturn( result );
        
        mojo.sign( jarSigner, request, 1 );
    }
    
    @Test
    public void testSignFailureOnFirstSuccessOnSecond() throws MojoExecutionException, JavaToolException {
        AbstractJarsignerMojo mojo = Mockito.mock(AbstractJarsignerMojo.class);
        JarSigner jarSigner = Mockito.mock( JarSigner.class );
        JarSignerRequest request = Mockito.mock( JarSignerRequest.class );
        Mockito.doCallRealMethod().when( mojo).sign( jarSigner,  request, 2 );
        JavaToolResult result1 = new JavaToolResult();
        result1.setExitCode( 1 );
        JavaToolResult result2 = new JavaToolResult();
        result2.setExitCode( 0 );
        Mockito.when( jarSigner.execute( request )).thenReturn( result1 ).thenReturn( result2 );
        
        mojo.sign( jarSigner, request, 2 );
    }
    
    @Test(expected = MojoExecutionException.class)
    public void testSignFailureOnFirstFailureOnSecond() throws MojoExecutionException, JavaToolException {
        AbstractJarsignerMojo mojo = Mockito.mock(AbstractJarsignerMojo.class);
        JarSigner jarSigner = Mockito.mock( JarSigner.class );
        JarSignerRequest request = Mockito.mock( JarSignerRequest.class );
        Mockito.doCallRealMethod().when( mojo).sign( jarSigner,  request, 2 );
        JavaToolResult result1 = new JavaToolResult();
        result1.setExitCode( 1 );
        JavaToolResult result2 = new JavaToolResult();
        result2.setExitCode( 1 );
        Mockito.when( jarSigner.execute( request )).thenReturn( result1 ).thenReturn( result2 );
        
        mojo.sign( jarSigner, request, 2 );
    }
}
