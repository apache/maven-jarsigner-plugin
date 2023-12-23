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

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.apache.maven.shared.jarsigner.AbstractJarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerSignRequest;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest matcher(s) to match properties on a JarSignerRequest request instances
 */
class RequestMatchers {

    /** Matcher for parameters common for JarSignerRequest instances */
    private static class AbstractJarSignerRequestMatcher<T extends AbstractJarSignerRequest>
            extends TypeSafeMatcher<T> {
        private final String predicateDescription;
        private final Object value;
        private final Predicate<AbstractJarSignerRequest> predicate;

        private AbstractJarSignerRequestMatcher(
                String predicateDescription, Object value, Predicate<AbstractJarSignerRequest> predicate) {
            this.predicateDescription = predicateDescription;
            this.value = value;
            this.predicate = predicate;
        }

        @Override
        protected boolean matchesSafely(AbstractJarSignerRequest request) {
            return predicate.test(request);
        }

        @Override
        public void describeTo(Description description) {
            description
                    .appendText("request that ")
                    .appendText(predicateDescription)
                    .appendValue(value);
        }
    }

    /** Matcher for parameters specific to JarSignerSignRequest instances */
    private static class JarSignerSignRequestMatcher extends TypeSafeMatcher<JarSignerSignRequest> {
        private final String predicateDescription;
        private final Object value;
        private final Predicate<JarSignerSignRequest> predicate;

        private JarSignerSignRequestMatcher(
                String predicateDescription, Object value, Predicate<JarSignerSignRequest> predicate) {
            this.predicateDescription = predicateDescription;
            this.value = value;
            this.predicate = predicate;
        }

        @Override
        protected boolean matchesSafely(JarSignerSignRequest request) {
            return predicate.test(request);
        }

        @Override
        public void describeTo(Description description) {
            description
                    .appendText("request that ")
                    .appendText(predicateDescription)
                    .appendValue(value);
        }
    }

    /** Create a matcher that matches when the request is using a specific file name for the archive */
    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasFileName(String expectedFileName) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has archive file name ",
                expectedFileName,
                request -> request.getArchive().getPath().endsWith(expectedFileName));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasAlias(String alias) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has alias ", alias, request -> request.getAlias().equals(alias));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasArguments(String[] arguments) {
        return new AbstractJarSignerRequestMatcher<T>("has arguments ", arguments, request -> {
            List<String> haystack = Arrays.asList(request.getArguments());
            for (String argumentNeedle : arguments) {
                if (!haystack.contains(argumentNeedle)) {
                    return false;
                }
            }
            return true;
        });
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasKeystore(String keystore) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has keystore ", keystore, request -> request.getKeystore().equals(keystore));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasMaxMemory(String maxMemory) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has maxMemory ", maxMemory, request -> request.getMaxMemory().equals(maxMemory));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasProtectedAuthenticationPath(
            boolean protectedAuthenticationPath) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has protectedAuthenticationPath ",
                protectedAuthenticationPath,
                request -> request.isProtectedAuthenticationPath() == protectedAuthenticationPath);
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasProviderArg(String providerArg) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has providerArg ", providerArg, request -> request.getProviderArg()
                        .equals(providerArg));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasProviderClass(String providerClass) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has providerClass ", providerClass, request -> request.getProviderClass()
                        .equals(providerClass));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasProviderName(String providerName) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has providerName ", providerName, request -> request.getProviderName()
                        .equals(providerName));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasStorepass(String storepass) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has storepass ", storepass, request -> request.getStorepass().equals(storepass));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasStoretype(String storetype) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has storetype ", storetype, request -> request.getStoretype().equals(storetype));
    }

    static <T extends AbstractJarSignerRequest> TypeSafeMatcher<T> hasVerbose(boolean verbose) {
        return new AbstractJarSignerRequestMatcher<T>(
                "has verbose ", verbose, request -> request.isVerbose() == verbose);
    }

    /* ************************************ JarSignerSignRequest specific matchers ************************************/

    static TypeSafeMatcher<JarSignerSignRequest> hasKeypass(String keypass) {
        return new JarSignerSignRequestMatcher(
                "has keypass ", keypass, request -> request.getKeypass().equals(keypass));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasSigfile(String sigfile) {
        return new JarSignerSignRequestMatcher(
                "has sigfile ", sigfile, request -> request.getSigfile().equals(sigfile));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasTsa(String tsa) {
        return new JarSignerSignRequestMatcher(
                "has tsa ", tsa, request -> request.getTsaLocation().equals(tsa));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasTsacert(String tsacert) {
        return new JarSignerSignRequestMatcher(
                "has tsacert ", tsacert, request -> request.getTsaAlias().equals(tsacert));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasTsaPolicyid(String tsapolicyid) {
        return new JarSignerSignRequestMatcher("has tsapolicyid ", tsapolicyid, request -> request.getTsapolicyid()
                .equals(tsapolicyid));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasTsaDigestalg(String tsadigestalg) {
        return new JarSignerSignRequestMatcher("has tsadigestalg ", tsadigestalg, request -> request.getTsadigestalg()
                .equals(tsadigestalg));
    }

    static TypeSafeMatcher<JarSignerSignRequest> hasCertchain(String certchain) {
        return new JarSignerSignRequestMatcher("has certchain ", certchain, request -> request.getCertchain()
                .getPath()
                .equals(certchain));
    }
}
