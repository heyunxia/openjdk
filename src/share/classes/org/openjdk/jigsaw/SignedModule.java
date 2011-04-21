/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.Signature;
import java.security.SignatureException;
import java.security.Timestamp;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.openjdk.jigsaw.FileConstants.*;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;
import sun.security.validator.KeyStores;
import sun.security.validator.PKIXValidator;
import sun.security.validator.Validator;

public final class SignedModule {

    public final static class SignerParameters
        implements ModuleFileSigner.Parameters {

        private final Signature signatureAlgorithm;
        private final X509Certificate[] signerChain;
        private final URI tsaURI;

        public SignerParameters(Signature signature,
                                X509Certificate[] chain, URI tsaURI)
        {
            this.signatureAlgorithm = signature;
            this.signerChain = chain;
            this.tsaURI = tsaURI;
        }

        public Signature getSignatureAlgorithm()
        {
            return signatureAlgorithm;
        }

        public Certificate[] getSignerCertificateChain()
        {
            return signerChain;
        }

        public URI getTimestampingAuthority()
        {
            return tsaURI;
        }
    }

    public final static class PKCS7Signer implements ModuleFileSigner {

        public ModuleFile.SignatureType getSignatureType()
        {
            return ModuleFile.SignatureType.PKCS7;
        }

        public byte[] generateSignature(byte[] toBeSigned,
                                        ModuleFileSigner.Parameters params)
            throws SignatureException
        {
            // Compute the signature
            Signature signatureAlg = params.getSignatureAlgorithm();
            signatureAlg.update(toBeSigned);
            byte[] signature = signatureAlg.sign();

            // Create the PKCS #7 signed data message
            try {
                return PKCS7.generateSignedData(
                        signature,
                        (X509Certificate[])params.getSignerCertificateChain(),
                        toBeSigned, signatureAlg.getAlgorithm(),
                        params.getTimestampingAuthority());
            } catch (final CertificateException |
                           IOException | NoSuchAlgorithmException e) {
                throw new SignatureException(e);
            }
        }
    }


    public final static class VerifierParameters
        implements ModuleFileVerifier.Parameters
    {
        private final Set<X509Certificate> trustedCerts;

        public VerifierParameters() throws IOException
        {
            trustedCerts = KeyStores.getTrustedCerts(loadCACertsKeyStore());
        }

        public Collection<X509Certificate> getTrustedCerts()
        {
            return trustedCerts;
        }
    }

    /*
     * Loads the default system-level trusted CA certs store at
     * '${java.home}/lib/security/cacerts' unless overridden by the
     * 'org.openjdk.system.security.cacerts' system property.
     * The cert store must be in JKS format.
     */
    private static KeyStore loadCACertsKeyStore() throws IOException
    {
        KeyStore trustedCertStore = null;
        try {
            trustedCertStore = KeyStore.getInstance("JKS");
            try (FileInputStream inStream = AccessController.doPrivileged(
                 new PrivilegedExceptionAction<FileInputStream>() {
                     public FileInputStream run() throws IOException
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
            }
        } catch (PrivilegedActionException pae) {
            throw (IOException)pae.getCause();
        } catch (GeneralSecurityException gse) {
            throw new IOException(gse);
        }
        return trustedCertStore;
    }

    public final static class PKCS7Verifier implements ModuleFileVerifier {

        private final PKCS7 pkcs7;
        private final CertificateFactory cf;
        private final List<byte[]> calculatedHashes;
        private final List<byte[]> expectedHashes;

        public PKCS7Verifier(ModuleFileFormat.Reader reader)
            throws SignatureException
        {
            try {
                pkcs7 = new PKCS7(reader.getSignatureNoClone());
                expectedHashes =
                    parseSignedData(pkcs7.getContentInfo().getData());
                cf = CertificateFactory.getInstance("X.509");
            } catch (final IOException | CertificateException e) {
                throw new SignatureException(e);
            }
            this.calculatedHashes = reader.getCalculatedHashes();
        }

        private List<byte[]> parseSignedData(byte[] signedData)
            throws IOException
        {
            List<byte[]> hashes = new ArrayList<>();
            DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(signedData));
            do {
                short hashLength = dis.readShort();
                byte[] hash = new byte[hashLength];
                if (dis.read(hash) != hashLength)
                    throw new IOException("invalid hash length in "
                                          + "signed data");
                hashes.add(hash);
            } while (dis.available() > 0);

            if (dis.available() != 0)
                throw new IOException("extra data at end of signed data");

            // must be at least 3 hashes (header, module info, & whole file)
            if (hashes.size() < 3)
                throw new IOException("too few hashes in signed data");
            return hashes;
        }

        public ModuleFile.SignatureType getSignatureType() {
            return ModuleFile.SignatureType.PKCS7;
        }

        // ## Need to improve exception handling 
        public Set<CodeSigner> verifySignature(ModuleFileVerifier.Parameters
                                               parameters)
            throws SignatureException
        {
            try {
                VerifierParameters params = (VerifierParameters)parameters;
                PKIXValidator csValidator
                    = (PKIXValidator)Validator.getInstance(
                            Validator.TYPE_PKIX, Validator.VAR_CODE_SIGNING,
                            params.getTrustedCerts());

                X509Certificate[] arrayType = new X509Certificate[0];

                // Verify signature. This will return null if the signature
                // cannot be verified successfully.
                SignerInfo[] signerInfos = pkcs7.verify();
                if (signerInfos == null)
                    throw new SignatureException("Cannot verify module "
                                                 + "signature");

                // Assume one signer
                SignerInfo signerInfo = signerInfos[0];
                List<X509Certificate> certChain =
                    signerInfo.getCertificateChain(pkcs7);
                X509Certificate signerCert = certChain.get(0);

                Timestamp ts = signerInfo.getTimestamp();
                if (ts != null) {
                    // validate timestamp only if signer's cert is expired
                    Date notAfter = signerCert.getNotAfter();
                    if (notAfter.before(new Date())) {
                        // check that timestamp is within cert's validity
                        Date timestamp = ts.getTimestamp();
                        if (timestamp.before(notAfter) &&
                            timestamp.after(signerCert.getNotBefore())) {
                            PKIXValidator tsaValidator
                                = (PKIXValidator)Validator.getInstance(
                                   Validator.TYPE_PKIX,
                                   Validator.VAR_TSA_SERVER,
                                   params.getTrustedCerts());
                            // set validation times to timestamp
                            tsaValidator.getParameters().setDate(timestamp);
                            csValidator.getParameters().setDate(timestamp);
                            // validate TSA certificate chain
                            List<X509Certificate> tsChain
                                = (List<X509Certificate>)
                                ts.getSignerCertPath().getCertificates();
                            tsaValidator.validate(tsChain.toArray(arrayType));
                        } else {
                            throw new SignatureException(
                                "the timestamp is not within the validity " +
                                "period of the signer's certificate");
                        }
                    }
                }

                // validate signer's certificate chain
                csValidator.validate(certChain.toArray(arrayType));
                CertPath certPath = cf.generateCertPath(certChain);
                return Collections.singleton(new CodeSigner(certPath, ts));

            } catch (final IOException | GeneralSecurityException e) {
                throw new SignatureException(e);
            }
        }

        public void verifyHashes(ModuleFileVerifier.Parameters parameters)
            throws SignatureException
        {
            verifyHashesStart(parameters);
            verifyHashesRest(parameters);
        }

        public void verifyHashesStart(ModuleFileVerifier.Parameters params)
            throws SignatureException
        {
            if (calculatedHashes.size() < 2)
                throw new SignatureException("Unexpected number of hashes");
            // check module header hash
            checkHashMatch(expectedHashes.get(0), calculatedHashes.get(0));
            // check module info hash
            checkHashMatch(expectedHashes.get(1), calculatedHashes.get(1));
        }

        public void verifyHashesRest(ModuleFileVerifier.Parameters params)
            throws SignatureException
        {
            if (calculatedHashes.size() != expectedHashes.size())
                throw new SignatureException("Unexpected number of hashes");

            for (int i = 2; i < expectedHashes.size(); i++) {
                checkHashMatch(expectedHashes.get(i), calculatedHashes.get(i));
            }
        }

        private void checkHashMatch(byte[] expected, byte[] computed)
            throws SignatureException
        {
            if (!MessageDigest.isEqual(expected, computed))
                throw new SignatureException("Expected hash "
                              + hashHexString(expected) + " instead of "
                              + hashHexString(computed));
        }
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
}
