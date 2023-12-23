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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.plugin.Mojo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parser of the generated META-INF/maven/plugin.xml for this project. The plugin.xml contains all mojos their
 * configuration parameters along with default values.
 */
public class PluginXmlParser {
    private static final String MOJO_IMPLEMENTATION_TAG = "implementation";
    private static final String MOJO_TAG = "mojo";
    private static final String CONF_DEFAULT_VALUE = "default-value";
    private static final String PLUGIN_XML_PATH = "META-INF/maven/plugin.xml";

    public static Map<String, String> getMojoDefaultConfiguration(Class<? extends Mojo> mojoClass) throws Exception {
        Map<String, String> defaultConfiguration = new LinkedHashMap<>();
        InputStream inputStream = PluginXmlParser.class.getClassLoader().getResourceAsStream(PLUGIN_XML_PATH);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        Element mojoElement = findMojoByClass(doc, mojoClass.getName());
        if (mojoElement == null) {
            throw new RuntimeException("Mojo not found for class: " + mojoClass.getName());
        }

        Element configurationElement =
                (Element) mojoElement.getElementsByTagName("configuration").item(0);
        NodeList configurationList = configurationElement.getChildNodes();
        for (int i = 0; i < configurationList.getLength(); i++) {
            Node child = configurationList.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            configurationElement = (Element) child;
            String configurationParameterName = configurationElement.getTagName();

            if (configurationElement.hasAttribute(CONF_DEFAULT_VALUE)) {
                String defaultValue = configurationElement.getAttribute(CONF_DEFAULT_VALUE);
                defaultConfiguration.put(configurationParameterName, defaultValue);
            } else {
                if (configurationElement.hasAttribute("implementation")) {
                    String implementation = configurationElement.getAttribute("implementation");
                    // String arrays are per default set to empty array if user does not configure them
                    if ("java.lang.String[]".equals(implementation)) {
                        defaultConfiguration.put(configurationParameterName, "");
                    }
                }
            }
        }
        return defaultConfiguration;
    }

    /** Searches in every mojo tag for an implementation tag matching the class name */
    private static Element findMojoByClass(Document doc, String className) {
        NodeList mojoList = doc.getElementsByTagName(MOJO_TAG);
        for (int i = 0; i < mojoList.getLength(); i++) {
            Element mojoElement = (Element) mojoList.item(i);
            String mojoClass = getTextContent(mojoElement, MOJO_IMPLEMENTATION_TAG);
            if (mojoClass.equals(className)) {
                return mojoElement;
            }
        }
        return null;
    }

    private static String getTextContent(Element parentElement, String tagName) {
        NodeList nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}
