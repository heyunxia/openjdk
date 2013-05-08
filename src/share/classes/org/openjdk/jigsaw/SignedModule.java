/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.openjdk.jigsaw;

import java.io.*;
import java.security.cert.Certificate;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;
import sun.security.validator.*;


/**
 * <p> A signed module file </p>
 */

public final class SignedModule {

    private static Set<X509Certificate> trustedCerts;
    private final ModuleFile.Reader mreader;
    private final PKCS7 pkcs7;
    private final List<byte[]> expectedHashes;

    public SignedModule(ModuleFile.Reader mr)
        throws IOException
    {
        mreader = mr;
        if (!mreader.hasSignature())
            throw new IOException("ModuleFile is not signed");

        pkcs7 = new PKCS7(mreader.getSignatureNoClone());
        ContentInfo ci = this.pkcs7.getContentInfo();

        // extract expected hashes from PKCS7 SignedData
        expectedHashes = new ArrayList<>();
        DataInputStream dis = new DataInputStream(
            new ByteArrayInputStream(ci.getData()));
        do {
            short hashLength = dis.readShort();
            byte[] hash = new byte[hashLength];
            if (dis.read(hash) != hashLength)
                throw new IOException("invalid hash length in signed data");
            expectedHashes.add(hash);
        } while (dis.available() > 0);

        if (dis.available() != 0)
            throw new IOException("extra data at end of signed data");

        // must be at least 3 hashes (header, module info, & whole file)
        if (expectedHashes.size() < 3)
            throw new IOException("too few hashes in signed data");
    }

    /**
     * Verifies a module file signature.
     *
     * @return the code signers of the module
     * @throws SignatureException if the signature is invalid
     */
    public Set<CodeSigner> verifySignature()
        throws SignatureException
    {
        try {
            // Verify signature. This will return null if the signature
            // cannot be verified successfully.
            SignerInfo[] signerInfos = pkcs7.verify();
            if (signerInfos == null)
                throw new SignatureException("Cannot verify module "
                                             + "signature");
            return toCodeSigners(signerInfos);
        } catch (GeneralSecurityException | IOException x) {
            throw new SignatureException(x);
        }
    }

    // converts an array of SignerInfo to a set of CodeSigner
    private Set<CodeSigner> toCodeSigners(SignerInfo[] signers)
        throws GeneralSecurityException, IOException
    {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Set<CodeSigner> codeSigners = new HashSet<>(signers.length);
        for (SignerInfo signer : signers) {
            List<X509Certificate> certChain = signer.getCertificateChain(pkcs7);
            CertPath certPath = cf.generateCertPath(certChain);
            codeSigners.add(new CodeSigner(certPath, signer.getTimestamp()));
        }
        return codeSigners;
    }

    /**
     * Verifies the module header hash and the module info hash.
     *
     * @throws SignatureException if either of the hashes don't match the
     *         expected hashes
     */
    public void verifyHashesStart()
        throws SignatureException
    {
        List<byte[]> calculatedHashes = mreader.getCalculatedHashes();
        if (calculatedHashes.size() < 2)
            throw new SignatureException("Unexpected number of hashes");
        // check module header hash
        checkHashMatch(expectedHashes.get(0), calculatedHashes.get(0));
        // check module info hash
        checkHashMatch(expectedHashes.get(1), calculatedHashes.get(1));
    }

    private static void checkHashMatch(byte[] expected, byte[] computed)
        throws SignatureException
    {
        if (!MessageDigest.isEqual(expected, computed))
            throw new SignatureException("Expected hash " +
                                         hashHexString(expected) +
                                         " instead of " +
                                         hashHexString(computed));
    }

    private static String hashHexString(byte[] hash) {
        StringBuilder hex = new StringBuilder("0x");
        for (int i = 0; i < hash.length; i++) {
            int val = (hash[i] & 0xFF);
            if (val <= 16)
                hex.append("0");
            hex.append(Integer.toHexString(val));
        }
        return hex.toString();
    }

    /**
     * Verifies the remaining hashes.
     *
     * @throws SignatureException if any of the hashes don't match the
     *         expected hashes
     */
    public void verifyHashesRest()
        throws SignatureException
    {
        List<byte[]> calculatedHashes = mreader.getCalculatedHashes();
        if (calculatedHashes.size() != expectedHashes.size())
            throw new SignatureException("Unexpected number of hashes");

        for (int i = 2; i < expectedHashes.size(); i++) {
            checkHashMatch(expectedHashes.get(i), calculatedHashes.get(i));
        }
    }

    /**
     * Validates the set of code signers. For each signer, the signer's
     * certificate chain, the timestamp (if not null), and the TSA's
     * certificate chain is validated. The set of most-trusted CA certificates
     * from the JRE cacerts file is used to validate the certificate chains.
     *
     * @param  signers
     *         the code signers
     *
     * @throws CertificateException
     *         if any of the code signers or timestamps are invalid
     */
    public static void validateSigners(Set<CodeSigner> signers)
        throws CertificateException
    {
        Set<X509Certificate> trustedCerts = getCACerts();
        PKIXValidator csValidator
            = (PKIXValidator)Validator.getInstance(Validator.TYPE_PKIX,
                                                   Validator.VAR_CODE_SIGNING,
                                                   trustedCerts);
        PKIXValidator tsaValidator
            = (PKIXValidator)Validator.getInstance(Validator.TYPE_PKIX,
                                                   Validator.VAR_TSA_SERVER,
                                                   trustedCerts);
        for (CodeSigner signer : signers) {
            validateSigner(signer, csValidator, tsaValidator);
        }
    }

    /*
     * Gets CA certs from the default system-level trusted CA certs store at
     * '${java.home}/lib/security/cacerts' unless overridden by the
     * 'org.openjdk.system.security.cacerts' system property.
     * The cert store must be in JKS format.
     */
    private static synchronized Set<X509Certificate> getCACerts()
        throws CertificateException
    {
        if (trustedCerts != null)
            return trustedCerts;

        try {
            KeyStore trustedCertStore = KeyStore.getInstance("JKS");
            try (InputStream inStream = AccessController.doPrivileged(
                 new PrivilegedExceptionAction<InputStream>() {
                     public InputStream run() throws IOException
                     {
                         return new FileInputStream(
                             System.getProperty(
                                 "org.openjdk.system.security.cacerts",
                                 System.getProperty("java.home")
                                     + "/lib/security/cacerts"));
                     }
                 }))
            {
                trustedCertStore.load(inStream, null);
                trustedCerts = KeyStores.getTrustedCerts(trustedCertStore);
                return trustedCerts;
            }
        } catch (PrivilegedActionException pae) {
            throw new CertificateException(pae.getCause());
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException x) {
            throw new CertificateException(x);
        }
    }

    private static void validateSigner(CodeSigner signer,
                                       PKIXValidator csValidator,
                                       PKIXValidator tsaValidator)
        throws CertificateException
    {
        // reset validity times to current date
        csValidator.getParameters().setDate(null);
        tsaValidator.getParameters().setDate(null);

        X509Certificate[] signerChain = getChain(signer.getSignerCertPath());
        X509Certificate signerCert = signerChain[0];
        Timestamp ts = signer.getTimestamp();
        if (ts != null) {
            // validate timestamp only if signer's cert is expired
            Date notAfter = signerCert.getNotAfter();
            if (notAfter.before(new Date())) {
                // check that timestamp is within cert's validity period
                Date timestamp = ts.getTimestamp();
                if (timestamp.before(notAfter) &&
                    timestamp.after(signerCert.getNotBefore())) {
                    // set validation times to timestamp
                    tsaValidator.getParameters().setDate(timestamp);
                    csValidator.getParameters().setDate(timestamp);
                    // validate TSA certificate chain
                    tsaValidator.validate(getChain(ts.getSignerCertPath()));
                } else {
                    throw new CertificateException(
                        "the timestamp is not within the validity " +
                        "period of the signer's certificate");
                }
            }
        }

        // validate signer's certificate chain
        csValidator.validate(signerChain);
    }

    private static X509Certificate[] getChain(CertPath certPath) {
        List<? extends Certificate> chain = certPath.getCertificates();
        return chain.toArray(new X509Certificate[0]);
    }

}
