/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.xml.parsers.xinclude;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/*
 * @bug 6794483
 * @summary Test JAXP parser can parse xml file using <xi:include> to include another xml, which has an empty element.
 */
public class Bug6794483Test {

    @Test
    public final void test() {
        String xml = getClass().getResource("test1.xml").getPath();
        Document doc = parseXmlFile(xml);

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);

        TransformerFactory transformerFact = TransformerFactory.newInstance();
        transformerFact.setAttribute("indent-number", new Integer(4));
        Transformer transformer;

        try {
            transformer = transformerFact.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/xml");

            // "true" indicate Append content If file exist in system
            transformer.transform(new DOMSource(doc), result);
            System.out.println("test" + sw);

        } catch (TransformerConfigurationException ex) {
            ex.printStackTrace();
            Assert.fail("unexpected TransformerConfigurationException");
        } catch (TransformerException ex) {
            ex.printStackTrace();
            Assert.fail("unexpected TransformerException");
        }

    }

    public Document parseXmlFile(String fileName) {
        System.out.println("Parsing XML file... " + fileName);
        DocumentBuilder docBuilder = null;
        Document doc = null;
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setCoalescing(true);
        docBuilderFactory.setXIncludeAware(true);
        System.out.println("Include: " + docBuilderFactory.isXIncludeAware());
        docBuilderFactory.setNamespaceAware(true);
        docBuilderFactory.setExpandEntityReferences(true);

        try {
            docBuilder = docBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        File sourceFile = new File(fileName);
        try {
            doc = docBuilder.parse(sourceFile);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("XML file parsed");
        return doc;

    }
}
