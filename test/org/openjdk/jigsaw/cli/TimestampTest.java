/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

import org.openjdk.jigsaw.cli.Librarian;
import org.openjdk.jigsaw.cli.Packager;
import org.openjdk.jigsaw.cli.Signer;

// copied from test/sun/security/tools/jarsigner and adapted for use with
// jigsaw signed modules
public class TimestampTest {
    private static final String BASE_DIR = System.getProperty("test.src", ".");
    private static final String MNAME = "test.security";
    private static final String ZLIB = "z.lib";
    private int port;
    private String[] jsignArgs = {
        "-v",
        "--keystore",
        "keystore.jks"
    };

    private String[] jmodArgs = {
        "-L",
        ZLIB,
        "install",
        MNAME + "@0.1.jmod"
    };

    private String[] jpkgArgs = {
        "-m",
        "z.modules/" + MNAME,
        "jmod",
        MNAME
    };

    public static void main(String[] args) throws Exception {
        new TimestampTest().run();
    }

    void run() throws Exception {

        Handler h = new Handler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        HttpContext ctx = server.createContext("/", h);
        server.start();

        System.setProperty("org.openjdk.system.security.cacerts",
                           "keystore.jks");
        System.setProperty("java.security.egd", "file:/dev/./urandom");
        try {
            test();
        } finally {
            server.stop(0);
        }
        System.out.println("All tests passed");
    }

    void test() throws Exception {
        testExpiredWithValidTimestamp();
        testNonExpiredWithValidTimestamp();
        testExpiredWithInvalidTimestamp();
    }

    void testNonExpiredWithValidTimestamp() throws Exception {
        newLibrary();
        sign("signer", 0);
        install();
    }

    void testExpiredWithInvalidTimestamp() throws Exception {
        newLibrary();
        sign("expired-signer", 0);
        try {
            install();
            throw new Exception("Expected SignatureException");
        } catch (Exception e) {
            if (!(e.getCause() instanceof SignatureException))
                throw e;
            System.out.println(e.getCause());
        }
    }

    void testExpiredWithValidTimestamp() throws Exception {
        newLibrary();
        sign("expired-signer", 1);
        install();
    }

    void newLibrary() {
        File zlib = new File(ZLIB);
        if (zlib.exists()) {
            if (!deleteAll(zlib))
                throw new RuntimeException("FATAL: removal of " + zlib + " failed.");
        }
        Librarian.main(new String[]{"-L", ZLIB, "create"});
    }

    /**
     * Delete a file or a directory (including all its contents).
     */
    boolean deleteAll(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles())
                deleteAll(f);
        }
        return file.delete();
    }

    void sign(String alias, int type) throws Exception {
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(jpkgArgs));
        Packager.run(args.toArray(new String[0]));
        args = new ArrayList<>();
        args.addAll(Arrays.asList(jsignArgs));
        args.add("--tsa");
        args.add("http://localhost:" + port + "/" + type);
        args.add(MNAME + "@0.1.jmod");
        args.add(alias);
        Signer.run(args.toArray(new String[0]));
    }

    void install() throws Exception {
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(jmodArgs));
        Librarian.run(args.toArray(new String[0]));
    }

    static class Handler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Got request");
            int len = 0;
            for (String h: t.getRequestHeaders().keySet()) {
                if (h.equalsIgnoreCase("Content-length")) {
                    len = Integer.valueOf(t.getRequestHeaders().get(h).get(0));
                }
            }
            byte[] input = new byte[len];
            t.getRequestBody().read(input);

            try {
                int path = 0;
                if (t.getRequestURI().getPath().length() > 1) {
                    path = Integer.parseInt(
                            t.getRequestURI().getPath().substring(1));
                }
                byte[] output = sign(input, path);
                Headers out = t.getResponseHeaders();
                out.set("Content-Type", "application/timestamp-reply");

                t.sendResponseHeaders(200, output.length);
                OutputStream os = t.getResponseBody();
                os.write(output);
            } catch (Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
            t.close();
        }

        /**
         * @param input The data to sign
         * @param path different cases to simulate, impl on URL path
         * 0: normal
         * 1: backdate timestamp
         * @returns the signed
         */
        byte[] sign(byte[] input, int path) throws Exception {
            // Read TSRequest
            DerValue value = new DerValue(input);
            System.err.println("\nIncoming Request\n===================");
            System.err.println("Version: " + value.data.getInteger());
            DerValue messageImprint = value.data.getDerValue();
            AlgorithmId aid = AlgorithmId.parse(
                    messageImprint.data.getDerValue());
            System.err.println("AlgorithmId: " + aid);

            BigInteger nonce = null;
            while (value.data.available() > 0) {
                DerValue v = value.data.getDerValue();
                if (v.tag == DerValue.tag_Integer) {
                    nonce = v.getBigInteger();
                    System.err.println("nonce: " + nonce);
                } else if (v.tag == DerValue.tag_Boolean) {
                    System.err.println("certReq: " + v.getBoolean());
                }
            }

            // Write TSResponse
            System.err.println("\nResponse\n===================");
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("keystore.jks"),
                    "test123".toCharArray());

            String alias = "tsa";

            DerOutputStream statusInfo = new DerOutputStream();
            statusInfo.putInteger(0);

            DerOutputStream token = new DerOutputStream();
            AlgorithmId[] algorithms = {aid};
            Certificate[] chain = ks.getCertificateChain(alias);
            X509Certificate signer = (X509Certificate)chain[0];
            X509Certificate[] signerCertificateChain = new X509Certificate[1];
            signerCertificateChain[0] = signer;

            DerOutputStream tst = new DerOutputStream();

            tst.putInteger(1);
            tst.putOID(new ObjectIdentifier("1.2.3.4"));    // policy

            tst.putDerValue(messageImprint);
            tst.putInteger(1);

            if (path != 1) {
                Calendar cal = Calendar.getInstance();
                tst.putGeneralizedTime(cal.getTime());
            } else {
                // expired-signed is Valid from: Sat Jan 01 11:32:36 EST 2005
                // until: Sun Jan 01 11:32:36 EST 2006
                // timestamp with a date within that validity period
                tst.putGeneralizedTime(new Date(1120190400000l)); // 07/01/2005
            }

            tst.putInteger(nonce);

            DerOutputStream tstInfo = new DerOutputStream();
            tstInfo.write(DerValue.tag_Sequence, tst);

            DerOutputStream tstInfo2 = new DerOutputStream();
            tstInfo2.putOctetString(tstInfo.toByteArray());

            Signature sig = Signature.getInstance("SHA1withDSA");
            sig.initSign((PrivateKey)(ks.getKey(
                    alias, "test123".toCharArray())));
            sig.update(tstInfo.toByteArray());

            ContentInfo contentInfo = new ContentInfo(new ObjectIdentifier(
                    "1.2.840.113549.1.9.16.1.4"),
                    new DerValue(tstInfo2.toByteArray()));

            System.err.println("Signing...");
            System.err.println(new X500Name(signer
                    .getIssuerX500Principal().getName()));
            System.err.println(signer.getSerialNumber());

            SignerInfo signerInfo = new SignerInfo(
                    new X500Name(signer.getIssuerX500Principal().getName()),
                    signer.getSerialNumber(),
                    AlgorithmId.get("SHA1"), AlgorithmId.get("DSA"), sig.sign());

            SignerInfo[] signerInfos = {signerInfo};
            PKCS7 p7 =
                    new PKCS7(algorithms, contentInfo, signerCertificateChain,
                    signerInfos);
            ByteArrayOutputStream p7out = new ByteArrayOutputStream();
            p7.encodeSignedData(p7out);

            DerOutputStream response = new DerOutputStream();
            response.write(DerValue.tag_Sequence, statusInfo);
            response.putDerValue(new DerValue(p7out.toByteArray()));

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.tag_Sequence, response);

            return out.toByteArray();
        }
    }
}
