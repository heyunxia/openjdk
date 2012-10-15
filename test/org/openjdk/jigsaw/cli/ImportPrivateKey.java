/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.security.spec.*;
import sun.misc.BASE64Decoder;

/**
 * Utility class to read a PKCS8 encoded key and its associated
 * X.509 Certificate from two files and import them into a JKS keystore.
 */
public class ImportPrivateKey {

    private static final String BASE_DIR = System.getProperty("test.src", ".");
    private static final String KEYSTORE_FILENAME = "keystore.jks";
    private static final char[] KEYSTORE_PASSWORD = "test123".toCharArray();

    /**
     * Synopsis:
     *   ImportPrivateKey [alias] [privateKeyFileName] [keyType] [certFileName]
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length != 4)
            throw new Exception("Usage: ImportPrivateKey [alias] " +
                                "[privateKeyFileName] [keyType] " +
                                "[certFileName]");
        PrivateKey privateKey = getPrivateKey(args[1], args[2]);
        Certificate[] publicCerts = getX509Certificates(args[3]);

        File keystoreFile = new File(KEYSTORE_FILENAME);
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(keystoreFile), KEYSTORE_PASSWORD);
        keystore.setKeyEntry(args[0], privateKey, KEYSTORE_PASSWORD,
                             publicCerts);
        OutputStream stream = new FileOutputStream(KEYSTORE_FILENAME);
        keystore.store(stream, KEYSTORE_PASSWORD);
        System.out.println("Imported private key and certificate into "
                           + "'" + args[0] + "' entry of JKS keystore");
        stream.close();
    }

    private static PrivateKey getPrivateKey(String keyFileName, String keyType)
        throws Exception
    {
        int headerLen = 28; // -----BEGIN PRIVATE KEY-----\n
        int trailerLen = 26; // -----END PRIVATE KEY-----\n
        File keyFile = new File(BASE_DIR, keyFileName);
        ByteBuffer keyBytes =
            ByteBuffer.allocate((int)keyFile.length() - headerLen - trailerLen);
        FileInputStream stream = new FileInputStream(keyFile);
        stream.getChannel().read(keyBytes, headerLen);
        byte[] decodedBytes =
            new BASE64Decoder().decodeBuffer(new String(keyBytes.array()));
        KeySpec keyEncoding = new PKCS8EncodedKeySpec(decodedBytes);
        stream.close();

        return KeyFactory.getInstance(keyType).generatePrivate(keyEncoding);
    }

    private static Certificate[] getX509Certificates(String certFileName)
        throws Exception
    {
        File certFile = new File(BASE_DIR, certFileName);
        InputStream stream = new FileInputStream(certFile);
        Certificate[] certificates = new Certificate[] {
            CertificateFactory.getInstance("X.509").generateCertificate(stream)
        };
        stream.close();

        return certificates;
    }
}
