/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.security.spec.*;
import sun.misc.BASE64Decoder;

/**
 * Utility class to read a PKCS8 encoded RSA key and its associated 
 * X.509 Certificate from two files and import them into a JKS keystore.
 */
public class ImportPrivateKey {

    private static final String BASE_DIR = System.getProperty("test.src", ".");
    private static final String KEY_FILENAME = "prikey.pem";
    private static final String CERT_FILENAME = "ee-cert.pem";
    private static final String KEYSTORE_FILENAME = "keystore.jks";
    private static final String KEYSTORE_ALIAS = "mykey";
    private static final char[] KEYSTORE_PASSWORD = "test123".toCharArray();

    public static void main(String[] args) throws Exception
    {
        PrivateKey privateKey = getRSAKey();
        Certificate[] publicCerts = getX509Certificates();

        File keystoreFile = new File(KEYSTORE_FILENAME);
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(keystoreFile), KEYSTORE_PASSWORD);
        keystore.setKeyEntry(KEYSTORE_ALIAS, privateKey, KEYSTORE_PASSWORD,
                             publicCerts);
        OutputStream stream = new FileOutputStream(KEYSTORE_FILENAME);
        keystore.store(stream, KEYSTORE_PASSWORD);
        System.out.println("Imported private key and certificate into "
                           + "'" + KEYSTORE_ALIAS + "' entry of JKS keystore");
        stream.close();
    }

    private static PrivateKey getRSAKey() throws Exception
    {
        int headerLen = 28; // -----BEGIN PRIVATE KEY-----\n
        int trailerLen = 26; // -----END PRIVATE KEY-----\n
        File keyFile = new File(BASE_DIR, KEY_FILENAME);
        ByteBuffer keyBytes =
            ByteBuffer.allocate((int)keyFile.length() - headerLen - trailerLen);
        FileInputStream stream = new FileInputStream(keyFile);
        stream.getChannel().read(keyBytes, headerLen);
        byte[] decodedBytes =
            new BASE64Decoder().decodeBuffer(new String(keyBytes.array()));
        KeySpec keyEncoding = new PKCS8EncodedKeySpec(decodedBytes);
        stream.close();

        return KeyFactory.getInstance("RSA").generatePrivate(keyEncoding);
    }

    private static Certificate[] getX509Certificates() throws Exception
    {
        File certFile = new File(BASE_DIR, CERT_FILENAME);
        InputStream stream = new FileInputStream(certFile);
        Certificate[] certificates = new Certificate[] {
            CertificateFactory.getInstance("X.509").generateCertificate(stream)
        };
        stream.close();

        return certificates;
    }
}
