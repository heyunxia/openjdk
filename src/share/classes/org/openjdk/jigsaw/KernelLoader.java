/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package org.openjdk.jigsaw;

import java.lang.module.*;
import java.lang.reflect.Module;
import java.util.*;

import static org.openjdk.jigsaw.Trace.*;


final class KernelLoader
    extends Loader
{

    private static class OtherPackages {

	// 600 > 437 / .75
	private static Set<String> packages = new HashSet<String>(600);

	static {
	    Set<String> p = packages;
	    // 437
	    p.add("com.sun.accessibility.internal.resources");
	    p.add("com.sun.activation.registries");
	    p.add("com.sun.awt");
	    p.add("com.sun.beans");
	    p.add("com.sun.beans.decoder");
	    p.add("com.sun.beans.finder");
	    p.add("com.sun.corba.se.impl.activation");
	    p.add("com.sun.corba.se.impl.copyobject");
	    p.add("com.sun.corba.se.impl.corba");
	    p.add("com.sun.corba.se.impl.dynamicany");
	    p.add("com.sun.corba.se.impl.encoding");
	    p.add("com.sun.corba.se.impl.interceptors");
	    p.add("com.sun.corba.se.impl.io");
	    p.add("com.sun.corba.se.impl.ior");
	    p.add("com.sun.corba.se.impl.ior.iiop");
	    p.add("com.sun.corba.se.impl.javax.rmi");
	    p.add("com.sun.corba.se.impl.javax.rmi.CORBA");
	    p.add("com.sun.corba.se.impl.legacy.connection");
	    p.add("com.sun.corba.se.impl.logging");
	    p.add("com.sun.corba.se.impl.monitoring");
	    p.add("com.sun.corba.se.impl.naming.cosnaming");
	    p.add("com.sun.corba.se.impl.naming.namingutil");
	    p.add("com.sun.corba.se.impl.naming.pcosnaming");
	    p.add("com.sun.corba.se.impl.oa");
	    p.add("com.sun.corba.se.impl.oa.poa");
	    p.add("com.sun.corba.se.impl.oa.toa");
	    p.add("com.sun.corba.se.impl.orb");
	    p.add("com.sun.corba.se.impl.orbutil");
	    p.add("com.sun.corba.se.impl.orbutil.closure");
	    p.add("com.sun.corba.se.impl.orbutil.concurrent");
	    p.add("com.sun.corba.se.impl.orbutil.fsm");
	    p.add("com.sun.corba.se.impl.orbutil.graph");
	    p.add("com.sun.corba.se.impl.orbutil.threadpool");
	    p.add("com.sun.corba.se.impl.presentation.rmi");
	    p.add("com.sun.corba.se.impl.protocol");
	    p.add("com.sun.corba.se.impl.protocol.giopmsgheaders");
	    p.add("com.sun.corba.se.impl.resolver");
	    p.add("com.sun.corba.se.impl.transport");
	    p.add("com.sun.corba.se.impl.util");
	    p.add("com.sun.corba.se.internal.CosNaming");
	    p.add("com.sun.corba.se.internal.Interceptors");
	    p.add("com.sun.corba.se.internal.POA");
	    p.add("com.sun.corba.se.internal.corba");
	    p.add("com.sun.corba.se.internal.iiop");
	    p.add("com.sun.corba.se.org.omg.CORBA");
	    p.add("com.sun.corba.se.pept.broker");
	    p.add("com.sun.corba.se.pept.encoding");
	    p.add("com.sun.corba.se.pept.protocol");
	    p.add("com.sun.corba.se.pept.transport");
	    p.add("com.sun.corba.se.spi.activation");
	    p.add("com.sun.corba.se.spi.activation.InitialNameServicePackage");
	    p.add("com.sun.corba.se.spi.activation.LocatorPackage");
	    p.add("com.sun.corba.se.spi.activation.RepositoryPackage");
	    p.add("com.sun.corba.se.spi.copyobject");
	    p.add("com.sun.corba.se.spi.encoding");
	    p.add("com.sun.corba.se.spi.extension");
	    p.add("com.sun.corba.se.spi.ior");
	    p.add("com.sun.corba.se.spi.ior.iiop");
	    p.add("com.sun.corba.se.spi.legacy.connection");
	    p.add("com.sun.corba.se.spi.legacy.interceptor");
	    p.add("com.sun.corba.se.spi.logging");
	    p.add("com.sun.corba.se.spi.monitoring");
	    p.add("com.sun.corba.se.spi.oa");
	    p.add("com.sun.corba.se.spi.orb");
	    p.add("com.sun.corba.se.spi.orbutil.closure");
	    p.add("com.sun.corba.se.spi.orbutil.fsm");
	    p.add("com.sun.corba.se.spi.orbutil.proxy");
	    p.add("com.sun.corba.se.spi.orbutil.threadpool");
	    p.add("com.sun.corba.se.spi.presentation.rmi");
	    p.add("com.sun.corba.se.spi.protocol");
	    p.add("com.sun.corba.se.spi.resolver");
	    p.add("com.sun.corba.se.spi.servicecontext");
	    p.add("com.sun.corba.se.spi.transport");
	    p.add("com.sun.demo.jvmti.hprof");
	    p.add("com.sun.image.codec.jpeg");
	    p.add("com.sun.imageio.plugins.bmp");
	    p.add("com.sun.imageio.plugins.common");
	    p.add("com.sun.imageio.plugins.gif");
	    p.add("com.sun.imageio.plugins.jpeg");
	    p.add("com.sun.imageio.plugins.png");
	    p.add("com.sun.imageio.plugins.wbmp");
	    p.add("com.sun.imageio.spi");
	    p.add("com.sun.imageio.stream");
	    p.add("com.sun.istack.internal");
	    p.add("com.sun.java.browser.dom");
	    p.add("com.sun.java.browser.net");
	    p.add("com.sun.java.swing");
	    p.add("com.sun.java.swing.plaf.gtk");
	    p.add("com.sun.java.swing.plaf.gtk.resources");
	    p.add("com.sun.java.swing.plaf.motif");
	    p.add("com.sun.java.swing.plaf.motif.resources");
	    p.add("com.sun.java.swing.plaf.windows");
	    p.add("com.sun.java.swing.plaf.windows.resources");
	    p.add("com.sun.java.util.jar.pack");
	    p.add("com.sun.java_cup.internal.runtime");
	    p.add("com.sun.jmx.defaults");
	    p.add("com.sun.jmx.event");
	    p.add("com.sun.jmx.interceptor");
	    p.add("com.sun.jmx.mbeanserver");
	    p.add("com.sun.jmx.namespace");
	    p.add("com.sun.jmx.namespace.serial");
	    p.add("com.sun.jmx.remote.internal");
	    p.add("com.sun.jmx.remote.protocol.iiop");
	    p.add("com.sun.jmx.remote.protocol.rmi");
	    p.add("com.sun.jmx.remote.security");
	    p.add("com.sun.jmx.remote.util");
	    p.add("com.sun.jmx.snmp");
	    p.add("com.sun.jmx.snmp.IPAcl");
	    p.add("com.sun.jmx.snmp.agent");
	    p.add("com.sun.jmx.snmp.daemon");
	    p.add("com.sun.jmx.snmp.defaults");
	    p.add("com.sun.jmx.snmp.internal");
	    p.add("com.sun.jmx.snmp.mpm");
	    p.add("com.sun.jmx.snmp.tasks");
	    p.add("com.sun.jmx.trace");
	    p.add("com.sun.jndi.cosnaming");
	    p.add("com.sun.jndi.dns");
	    p.add("com.sun.jndi.ldap");
	    p.add("com.sun.jndi.ldap.ext");
	    p.add("com.sun.jndi.ldap.pool");
	    p.add("com.sun.jndi.ldap.sasl");
	    p.add("com.sun.jndi.rmi.registry");
	    p.add("com.sun.jndi.toolkit.corba");
	    p.add("com.sun.jndi.toolkit.ctx");
	    p.add("com.sun.jndi.toolkit.dir");
	    p.add("com.sun.jndi.toolkit.url");
	    p.add("com.sun.jndi.url.corbaname");
	    p.add("com.sun.jndi.url.dns");
	    p.add("com.sun.jndi.url.iiop");
	    p.add("com.sun.jndi.url.iiopname");
	    p.add("com.sun.jndi.url.ldap");
	    p.add("com.sun.jndi.url.ldaps");
	    p.add("com.sun.jndi.url.rmi");
	    p.add("com.sun.management");
	    p.add("com.sun.management.jmx");
	    p.add("com.sun.media.sound");
	    p.add("com.sun.naming.internal");
	    p.add("com.sun.net.httpserver");
	    p.add("com.sun.net.httpserver.spi");
	    p.add("com.sun.net.ssl");
	    p.add("com.sun.net.ssl.internal.ssl");
	    p.add("com.sun.net.ssl.internal.www.protocol.https");
	    p.add("com.sun.nio.file");
	    p.add("com.sun.org.apache.bcel.internal");
	    p.add("com.sun.org.apache.bcel.internal.classfile");
	    p.add("com.sun.org.apache.bcel.internal.generic");
	    p.add("com.sun.org.apache.bcel.internal.util");
	    p.add("com.sun.org.apache.regexp.internal");
	    p.add("com.sun.org.apache.xalan.internal");
	    p.add("com.sun.org.apache.xalan.internal.client");
	    p.add("com.sun.org.apache.xalan.internal.extensions");
	    p.add("com.sun.org.apache.xalan.internal.lib");
	    p.add("com.sun.org.apache.xalan.internal.res");
	    p.add("com.sun.org.apache.xalan.internal.templates");
	    p.add("com.sun.org.apache.xalan.internal.xslt");
	    p.add("com.sun.org.apache.xalan.internal.xsltc");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.cmdline");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.cmdline.getopt");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.compiler");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.compiler.util");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.dom");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.runtime");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.runtime.output");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.trax");
	    p.add("com.sun.org.apache.xalan.internal.xsltc.util");
	    p.add("com.sun.org.apache.xerces.internal.dom");
	    p.add("com.sun.org.apache.xerces.internal.dom.events");
	    p.add("com.sun.org.apache.xerces.internal.impl");
	    p.add("com.sun.org.apache.xerces.internal.impl.dtd");
	    p.add("com.sun.org.apache.xerces.internal.impl.dtd.models");
	    p.add("com.sun.org.apache.xerces.internal.impl.dv");
	    p.add("com.sun.org.apache.xerces.internal.impl.dv.dtd");
	    p.add("com.sun.org.apache.xerces.internal.impl.dv.util");
	    p.add("com.sun.org.apache.xerces.internal.impl.dv.xs");
	    p.add("com.sun.org.apache.xerces.internal.impl.io");
	    p.add("com.sun.org.apache.xerces.internal.impl.msg");
	    p.add("com.sun.org.apache.xerces.internal.impl.validation");
	    p.add("com.sun.org.apache.xerces.internal.impl.xpath");
	    p.add("com.sun.org.apache.xerces.internal.impl.xpath.regex");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs.identity");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs.models");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs.opti");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs.traversers");
	    p.add("com.sun.org.apache.xerces.internal.impl.xs.util");
	    p.add("com.sun.org.apache.xerces.internal.jaxp");
	    p.add("com.sun.org.apache.xerces.internal.jaxp.datatype");
	    p.add("com.sun.org.apache.xerces.internal.jaxp.validation");
	    p.add("com.sun.org.apache.xerces.internal.parsers");
	    p.add("com.sun.org.apache.xerces.internal.util");
	    p.add("com.sun.org.apache.xerces.internal.xinclude");
	    p.add("com.sun.org.apache.xerces.internal.xni");
	    p.add("com.sun.org.apache.xerces.internal.xni.grammars");
	    p.add("com.sun.org.apache.xerces.internal.xni.parser");
	    p.add("com.sun.org.apache.xerces.internal.xpointer");
	    p.add("com.sun.org.apache.xerces.internal.xs");
	    p.add("com.sun.org.apache.xerces.internal.xs.datatypes");
	    p.add("com.sun.org.apache.xml.internal.dtm");
	    p.add("com.sun.org.apache.xml.internal.dtm.ref");
	    p.add("com.sun.org.apache.xml.internal.dtm.ref.dom2dtm");
	    p.add("com.sun.org.apache.xml.internal.dtm.ref.sax2dtm");
	    p.add("com.sun.org.apache.xml.internal.res");
	    p.add("com.sun.org.apache.xml.internal.resolver");
	    p.add("com.sun.org.apache.xml.internal.resolver.helpers");
	    p.add("com.sun.org.apache.xml.internal.resolver.readers");
	    p.add("com.sun.org.apache.xml.internal.resolver.tools");
	    p.add("com.sun.org.apache.xml.internal.security");
	    p.add("com.sun.org.apache.xml.internal.security.algorithms");
	    p.add("com.sun.org.apache.xml.internal.security.algorithms.implementations");
	    p.add("com.sun.org.apache.xml.internal.security.c14n");
	    p.add("com.sun.org.apache.xml.internal.security.c14n.helper");
	    p.add("com.sun.org.apache.xml.internal.security.c14n.implementations");
	    p.add("com.sun.org.apache.xml.internal.security.encryption");
	    p.add("com.sun.org.apache.xml.internal.security.exceptions");
	    p.add("com.sun.org.apache.xml.internal.security.keys");
	    p.add("com.sun.org.apache.xml.internal.security.keys.content");
	    p.add("com.sun.org.apache.xml.internal.security.keys.content.keyvalues");
	    p.add("com.sun.org.apache.xml.internal.security.keys.content.x509");
	    p.add("com.sun.org.apache.xml.internal.security.keys.keyresolver");
	    p.add("com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations");
	    p.add("com.sun.org.apache.xml.internal.security.keys.storage");
	    p.add("com.sun.org.apache.xml.internal.security.keys.storage.implementations");
	    p.add("com.sun.org.apache.xml.internal.security.signature");
	    p.add("com.sun.org.apache.xml.internal.security.transforms");
	    p.add("com.sun.org.apache.xml.internal.security.transforms.implementations");
	    p.add("com.sun.org.apache.xml.internal.security.transforms.params");
	    p.add("com.sun.org.apache.xml.internal.security.utils");
	    p.add("com.sun.org.apache.xml.internal.security.utils.resolver");
	    p.add("com.sun.org.apache.xml.internal.security.utils.resolver.implementations");
	    p.add("com.sun.org.apache.xml.internal.serialize");
	    p.add("com.sun.org.apache.xml.internal.serializer");
	    p.add("com.sun.org.apache.xml.internal.serializer.utils");
	    p.add("com.sun.org.apache.xml.internal.utils");
	    p.add("com.sun.org.apache.xml.internal.utils.res");
	    p.add("com.sun.org.apache.xpath.internal");
	    p.add("com.sun.org.apache.xpath.internal.axes");
	    p.add("com.sun.org.apache.xpath.internal.compiler");
	    p.add("com.sun.org.apache.xpath.internal.domapi");
	    p.add("com.sun.org.apache.xpath.internal.functions");
	    p.add("com.sun.org.apache.xpath.internal.jaxp");
	    p.add("com.sun.org.apache.xpath.internal.objects");
	    p.add("com.sun.org.apache.xpath.internal.operations");
	    p.add("com.sun.org.apache.xpath.internal.patterns");
	    p.add("com.sun.org.apache.xpath.internal.res");
	    p.add("com.sun.org.omg.CORBA");
	    p.add("com.sun.org.omg.CORBA.ValueDefPackage");
	    p.add("com.sun.org.omg.CORBA.portable");
	    p.add("com.sun.org.omg.SendingContext");
	    p.add("com.sun.org.omg.SendingContext.CodeBasePackage");
	    p.add("com.sun.rmi.rmid");
	    p.add("com.sun.rowset");
	    p.add("com.sun.rowset.internal");
	    p.add("com.sun.rowset.providers");
	    p.add("com.sun.script.javascript");
	    p.add("com.sun.script.util");
	    p.add("com.sun.security.auth");
	    p.add("com.sun.security.auth.callback");
	    p.add("com.sun.security.auth.login");
	    p.add("com.sun.security.auth.module");
	    p.add("com.sun.security.cert.internal.x509");
	    p.add("com.sun.security.jgss");
	    p.add("com.sun.security.sasl");
	    p.add("com.sun.security.sasl.digest");
	    p.add("com.sun.security.sasl.gsskerb");
	    p.add("com.sun.security.sasl.util");
	    p.add("com.sun.servicetag");
	    p.add("com.sun.swing.internal.plaf.basic.resources");
	    p.add("com.sun.swing.internal.plaf.metal.resources");
	    p.add("com.sun.swing.internal.plaf.synth.resources");
	    p.add("com.sun.tracing");
	    p.add("com.sun.tracing.dtrace");
	    p.add("com.sun.xml.internal.bind");
	    p.add("com.sun.xml.internal.bind.annotation");
	    p.add("com.sun.xml.internal.bind.api");
	    p.add("com.sun.xml.internal.bind.api.impl");
	    p.add("com.sun.xml.internal.bind.marshaller");
	    p.add("com.sun.xml.internal.bind.unmarshaller");
	    p.add("com.sun.xml.internal.bind.util");
	    p.add("com.sun.xml.internal.bind.v2");
	    p.add("com.sun.xml.internal.bind.v2.bytecode");
	    p.add("com.sun.xml.internal.bind.v2.model.annotation");
	    p.add("com.sun.xml.internal.bind.v2.model.core");
	    p.add("com.sun.xml.internal.bind.v2.model.impl");
	    p.add("com.sun.xml.internal.bind.v2.model.nav");
	    p.add("com.sun.xml.internal.bind.v2.model.runtime");
	    p.add("com.sun.xml.internal.bind.v2.runtime");
	    p.add("com.sun.xml.internal.bind.v2.runtime.output");
	    p.add("com.sun.xml.internal.bind.v2.runtime.property");
	    p.add("com.sun.xml.internal.bind.v2.runtime.reflect");
	    p.add("com.sun.xml.internal.bind.v2.runtime.reflect.opt");
	    p.add("com.sun.xml.internal.bind.v2.runtime.unmarshaller");
	    p.add("com.sun.xml.internal.bind.v2.schemagen");
	    p.add("com.sun.xml.internal.bind.v2.schemagen.xmlschema");
	    p.add("com.sun.xml.internal.bind.v2.util");
	    p.add("com.sun.xml.internal.fastinfoset");
	    p.add("com.sun.xml.internal.fastinfoset.algorithm");
	    p.add("com.sun.xml.internal.fastinfoset.alphabet");
	    p.add("com.sun.xml.internal.fastinfoset.dom");
	    p.add("com.sun.xml.internal.fastinfoset.org.apache.xerces.util");
	    p.add("com.sun.xml.internal.fastinfoset.sax");
	    p.add("com.sun.xml.internal.fastinfoset.stax");
	    p.add("com.sun.xml.internal.fastinfoset.stax.events");
	    p.add("com.sun.xml.internal.fastinfoset.stax.factory");
	    p.add("com.sun.xml.internal.fastinfoset.stax.util");
	    p.add("com.sun.xml.internal.fastinfoset.tools");
	    p.add("com.sun.xml.internal.fastinfoset.util");
	    p.add("com.sun.xml.internal.fastinfoset.vocab");
	    p.add("com.sun.xml.internal.messaging.saaj");
	    p.add("com.sun.xml.internal.messaging.saaj.client.p2p");
	    p.add("com.sun.xml.internal.messaging.saaj.packaging.mime");
	    p.add("com.sun.xml.internal.messaging.saaj.packaging.mime.internet");
	    p.add("com.sun.xml.internal.messaging.saaj.packaging.mime.util");
	    p.add("com.sun.xml.internal.messaging.saaj.soap");
	    p.add("com.sun.xml.internal.messaging.saaj.soap.dynamic");
	    p.add("com.sun.xml.internal.messaging.saaj.soap.impl");
	    p.add("com.sun.xml.internal.messaging.saaj.soap.name");
	    p.add("com.sun.xml.internal.messaging.saaj.soap.ver1_1");
	    p.add("com.sun.xml.internal.messaging.saaj.soap.ver1_2");
	    p.add("com.sun.xml.internal.messaging.saaj.util");
	    p.add("com.sun.xml.internal.messaging.saaj.util.transform");
	    p.add("com.sun.xml.internal.org.jvnet.fastinfoset");
	    p.add("com.sun.xml.internal.org.jvnet.fastinfoset.sax");
	    p.add("com.sun.xml.internal.org.jvnet.fastinfoset.sax.helpers");
	    p.add("com.sun.xml.internal.stream");
	    p.add("com.sun.xml.internal.stream.dtd");
	    p.add("com.sun.xml.internal.stream.dtd.nonvalidating");
	    p.add("com.sun.xml.internal.stream.events");
	    p.add("com.sun.xml.internal.stream.util");
	    p.add("com.sun.xml.internal.stream.writers");
	    p.add("com.sun.xml.internal.txw2");
	    p.add("com.sun.xml.internal.txw2.annotation");
	    p.add("com.sun.xml.internal.txw2.output");
	    p.add("com.sun.xml.internal.ws.binding");
	    p.add("com.sun.xml.internal.ws.binding.http");
	    p.add("com.sun.xml.internal.ws.binding.soap");
	    p.add("com.sun.xml.internal.ws.client");
	    p.add("com.sun.xml.internal.ws.client.dispatch");
	    p.add("com.sun.xml.internal.ws.client.dispatch.impl");
	    p.add("com.sun.xml.internal.ws.client.dispatch.impl.encoding");
	    p.add("com.sun.xml.internal.ws.client.dispatch.impl.protocol");
	    p.add("com.sun.xml.internal.ws.developer");
	    p.add("com.sun.xml.internal.ws.encoding");
	    p.add("com.sun.xml.internal.ws.encoding.internal");
	    p.add("com.sun.xml.internal.ws.encoding.jaxb");
	    p.add("com.sun.xml.internal.ws.encoding.simpletype");
	    p.add("com.sun.xml.internal.ws.encoding.soap");
	    p.add("com.sun.xml.internal.ws.encoding.soap.client");
	    p.add("com.sun.xml.internal.ws.encoding.soap.internal");
	    p.add("com.sun.xml.internal.ws.encoding.soap.message");
	    p.add("com.sun.xml.internal.ws.encoding.soap.server");
	    p.add("com.sun.xml.internal.ws.encoding.soap.streaming");
	    p.add("com.sun.xml.internal.ws.encoding.xml");
	    p.add("com.sun.xml.internal.ws.handler");
	    p.add("com.sun.xml.internal.ws.model");
	    p.add("com.sun.xml.internal.ws.model.soap");
	    p.add("com.sun.xml.internal.ws.modeler");
	    p.add("com.sun.xml.internal.ws.pept");
	    p.add("com.sun.xml.internal.ws.pept.encoding");
	    p.add("com.sun.xml.internal.ws.pept.ept");
	    p.add("com.sun.xml.internal.ws.pept.presentation");
	    p.add("com.sun.xml.internal.ws.pept.protocol");
	    p.add("com.sun.xml.internal.ws.protocol.soap.client");
	    p.add("com.sun.xml.internal.ws.protocol.soap.server");
	    p.add("com.sun.xml.internal.ws.protocol.xml");
	    p.add("com.sun.xml.internal.ws.protocol.xml.client");
	    p.add("com.sun.xml.internal.ws.protocol.xml.server");
	    p.add("com.sun.xml.internal.ws.server");
	    p.add("com.sun.xml.internal.ws.server.provider");
	    p.add("com.sun.xml.internal.ws.spi");
	    p.add("com.sun.xml.internal.ws.spi.runtime");
	    p.add("com.sun.xml.internal.ws.streaming");
	    p.add("com.sun.xml.internal.ws.transport");
	    p.add("com.sun.xml.internal.ws.transport.http.client");
	    p.add("com.sun.xml.internal.ws.transport.http.server");
	    p.add("com.sun.xml.internal.ws.transport.local");
	    p.add("com.sun.xml.internal.ws.transport.local.client");
	    p.add("com.sun.xml.internal.ws.transport.local.server");
	    p.add("com.sun.xml.internal.ws.util");
	    p.add("com.sun.xml.internal.ws.util.exception");
	    p.add("com.sun.xml.internal.ws.util.localization");
	    p.add("com.sun.xml.internal.ws.util.xml");
	    p.add("com.sun.xml.internal.ws.wsdl");
	    p.add("com.sun.xml.internal.ws.wsdl.parser");
	    p.add("com.sun.xml.internal.ws.wsdl.writer");
	    p.add("com.sun.xml.internal.ws.wsdl.writer.document");
	    p.add("com.sun.xml.internal.ws.wsdl.writer.document.http");
	    p.add("com.sun.xml.internal.ws.wsdl.writer.document.soap");
	    p.add("com.sun.xml.internal.ws.wsdl.writer.document.soap12");
	    p.add("com.sun.xml.internal.ws.wsdl.writer.document.xsd");
	    p.add("org.ietf.jgss");
	    p.add("org.jcp.xml.dsig.internal");
	    p.add("org.jcp.xml.dsig.internal.dom");
	    p.add("org.omg.CORBA");
	    p.add("org.omg.CORBA.DynAnyPackage");
	    p.add("org.omg.CORBA.ORBPackage");
	    p.add("org.omg.CORBA.TypeCodePackage");
	    p.add("org.omg.CORBA.portable");
	    p.add("org.omg.CORBA_2_3");
	    p.add("org.omg.CORBA_2_3.portable");
	    p.add("org.omg.CosNaming");
	    p.add("org.omg.CosNaming.NamingContextExtPackage");
	    p.add("org.omg.CosNaming.NamingContextPackage");
	    p.add("org.omg.Dynamic");
	    p.add("org.omg.DynamicAny");
	    p.add("org.omg.DynamicAny.DynAnyFactoryPackage");
	    p.add("org.omg.DynamicAny.DynAnyPackage");
	    p.add("org.omg.IOP");
	    p.add("org.omg.IOP.CodecFactoryPackage");
	    p.add("org.omg.IOP.CodecPackage");
	    p.add("org.omg.Messaging");
	    p.add("org.omg.PortableInterceptor");
	    p.add("org.omg.PortableInterceptor.ORBInitInfoPackage");
	    p.add("org.omg.PortableServer");
	    p.add("org.omg.PortableServer.CurrentPackage");
	    p.add("org.omg.PortableServer.POAManagerPackage");
	    p.add("org.omg.PortableServer.POAPackage");
	    p.add("org.omg.PortableServer.ServantLocatorPackage");
	    p.add("org.omg.PortableServer.portable");
	    p.add("org.omg.SendingContext");
	    p.add("org.omg.stub.java.rmi");
	    p.add("org.omg.stub.javax.management.remote.rmi");
	    p.add("org.w3c.dom");
	    p.add("org.w3c.dom.bootstrap");
	    p.add("org.w3c.dom.css");
	    p.add("org.w3c.dom.events");
	    p.add("org.w3c.dom.html");
	    p.add("org.w3c.dom.ls");
	    p.add("org.w3c.dom.ranges");
	    p.add("org.w3c.dom.stylesheets");
	    p.add("org.w3c.dom.traversal");
	    p.add("org.w3c.dom.views");
	    p.add("org.w3c.dom.xpath");
	    p.add("org.xml.sax");
	    p.add("org.xml.sax.ext");
	    p.add("org.xml.sax.helpers");
	    p.add("sunw.io");
	    p.add("sunw.util");
	}

    }

    boolean isKernelClass(String name) {
	int i = name.lastIndexOf('.');
	if (i < 0) return false;
	String pkg = name.substring(0, i);
	if (pkg.startsWith("java.")) return true;
	if (pkg.startsWith("javax.")) return true;
	if (pkg.startsWith("sun.")) return true;
	return OtherPackages.packages.contains(pkg);
    }

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static ModuleId KERNEL_MODULE_ID
	= jms.parseModuleId("jdk@7-ea");
    private static ModuleId KERNEL_PROVIDES
	= jms.parseModuleId("java@7-ea");

    private static class Info
	implements ModuleInfo
    {
	private static Set<ModuleId> provides
	    = Collections.singleton(KERNEL_PROVIDES);
	public ModuleId id() { return KERNEL_MODULE_ID; }
	public Set<ModuleId> provides() { return provides; }
	public Set<Dependence> requires() { return Collections.emptySet(); }
	public Set<String> permits() { return Collections.emptySet(); }
	public String mainClass() { return null; }
    }

    private Module module;

    Module module() { return module; }

    // ## Need a native version of this that doesn't throw CNFE
    private Class<?> findBootstrapClassOrNull(String name) {
        try {
            // ## return findBootstrapClass(name);
	    Class<?> c = findSystemClass(name);
	    sun.misc.SharedSecrets.getJavaLangAccess().setModule(c, module);
	    return c;
        } catch (ClassNotFoundException x) {
            return null;
        }
    }

    @Override
    public Class<?> loadClass(String cn)
	throws ClassNotFoundException
    {

	Class<?> c = null;

        if ((c = findLoadedClass(cn)) != null) {
	    if (tracing)
		trace(0, "%s (cache) %s", this, cn);
            return c;
	}

        if ((c = findBootstrapClassOrNull(cn)) != null) {
	    if (tracing)
		trace(0, "%s: load %s:%s", this, KERNEL_MODULE_ID, cn);
            return c;
	}

	throw new ClassNotFoundException(cn);

    }

    KernelLoader(LoaderPool lp) {
	super(lp, null);
	module = new Module(new Info(), this);
	addModule(module);
    }

    public String toString() {
	return "+kernel";
    }

}
