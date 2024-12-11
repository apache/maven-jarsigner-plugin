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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.jarsigner.JarsignerSignMojo.WaitStrategy;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.toolchain.ToolchainManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Creates and configures a Mojo instance to be used in testing.
 *
 * @param <T> The type of Mojo
 */
public class MojoTestCreator<T extends AbstractJarsignerMojo> {

    private final Logger logger = LoggerFactory.getLogger(MojoTestCreator.class);

    private final Class<T> clazz;
    private final MavenProject project;
    private final File projectDir;
    private final JarSigner jarSigner;
    private ToolchainManager toolchainManager;
    private SecDispatcher securityDispatcher;
    private WaitStrategy waitStrategy;
    private Log log;
    private List<Field> fields;

    public MojoTestCreator(Class<T> clazz, MavenProject project, File projectDir, JarSigner jarSigner)
            throws Exception {
        this.clazz = clazz;
        this.project = project;
        this.projectDir = projectDir;
        this.jarSigner = jarSigner;

        securityDispatcher = str -> str; // Simple SecDispatcher that only returns parameter
        fields = getAllFields(clazz);
    }

    public void setToolchainManager(ToolchainManager toolchainManager) {
        this.toolchainManager = toolchainManager;
    }

    public void setSecDispatcher(SecDispatcher securityDispatcher) {
        this.securityDispatcher = securityDispatcher;
    }

    public void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    /**
     * Creates and configures the Mojo instance.
     *
     * @param configuration user supplied configuration
     */
    public T configure(Map<String, String> configuration, Class<?>... parameterTypes) throws Exception {
        T mojo = clazz.getDeclaredConstructor(parameterTypes).newInstance();
        setDefaultValues(mojo);

        setAttribute(mojo, "project", project);
        setAttribute(mojo, "jarSigner", jarSigner);
        setAttribute(mojo, "securityDispatcher", securityDispatcher);
        if (toolchainManager != null) {
            setAttribute(mojo, "toolchainManager", toolchainManager);
        }
        if (waitStrategy != null) {
            ((JarsignerSignMojo) mojo).setWaitStrategy(waitStrategy);
        }
        if (log != null) {
            mojo.setLog(log);
        }

        for (Map.Entry<String, String> entry : configuration.entrySet()) {
            Field field = getField(mojo, entry.getKey());
            setFieldByStringValue(mojo, field, entry.getValue());
        }

        return mojo;
    }

    private void setFieldByStringValue(Object instance, Field field, String stringValue) throws Exception {
        field.setAccessible(true);
        Class<?> fieldType = field.getType();
        if (fieldType.equals(String.class)) {
            field.set(instance, stringValue);
        } else if (fieldType.equals(int.class)) {
            field.setInt(instance, Integer.parseInt(stringValue));
        } else if (fieldType.equals(boolean.class)) {
            field.setBoolean(instance, Boolean.parseBoolean(stringValue));
        } else if (fieldType.equals(File.class)) {
            field.set(instance, new File(stringValue));
        } else if (fieldType.equals(String[].class)) {
            if (stringValue.isEmpty()) {
                // Maven defaults to empty list if no default value exists
                field.set(instance, new String[0]);
            } else {
                String[] values = stringValue.split(",");
                field.set(instance, values);
            }
        } else {
            if (!stringValue.startsWith("${")) {
                logger.warn(
                        "Not implemented support to set of field of type {} to value {}",
                        fieldType.getSimpleName(),
                        stringValue);
            }
        }
    }

    private Field getField(Object instance, String fieldName) {
        return fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find field " + fieldName + " in class "
                        + instance.getClass().getName()));
    }

    private void setAttribute(Object instance, String fieldName, Object value) throws Exception {
        Field field = getField(instance, fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private void setDefaultValues(Mojo mojo) throws Exception {
        Map<String, String> defaultConfiguration = PluginXmlParser.getMojoDefaultConfiguration(mojo.getClass());
        for (String parameterName : defaultConfiguration.keySet()) {
            String defaultValue = defaultConfiguration.get(parameterName);
            defaultValue = substituteParameterValueVariables(defaultValue);
            Field field = getField(mojo, parameterName);
            setFieldByStringValue(mojo, field, defaultValue.toString());
        }
    }

    private String substituteParameterValueVariables(String parameterValue) {
        parameterValue = parameterValue.replaceAll(
                Pattern.quote("${project.basedir}"), Matcher.quoteReplacement(projectDir.getPath()));
        return parameterValue;
    }

    static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClazz = clazz;
        while (currentClazz != null) {
            fields.addAll(Arrays.asList(currentClazz.getDeclaredFields()));
            currentClazz = currentClazz.getSuperclass();
        }
        return fields;
    }
}
