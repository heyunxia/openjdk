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

package org.openjdk.jigsaw.cli;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.security.auth.DestroyFailedException;

import static java.lang.System.err;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.security.KeyStore.PasswordProtection;
import static java.security.KeyStore.PrivateKeyEntry;

import org.openjdk.jigsaw.*;
import static org.openjdk.jigsaw.ModuleFileFormat.Reader;
import static org.openjdk.jigsaw.ModuleFileFormat.SectionHeader;
import static org.openjdk.jigsaw.SignedModule.SignerParameters;
import org.openjdk.internal.joptsimple.OptionException;
import org.openjdk.internal.joptsimple.OptionParser;
import org.openjdk.internal.joptsimple.OptionSet;
import org.openjdk.internal.joptsimple.OptionSpec;

import sun.security.util.Password;

/* Interface:

jsign [-v] [--keystore <keystore-location>] \
      [--storetype <keystore-type>] [--protected] \
      [--tsa <url>] [--signedmodulefile <signed-module-file>] \
      <module-file> <signer-alias>

*/

public class Signer {

    private OptionParser parser;
    private boolean verbose = false;

    // Do not prompt for a keystore password (for example, with a keystore 
    // provider that is configured with its own PIN entry device).
    private boolean protectedPath = false;

    // Module signer's alias
    private String signer;

    // Module signer's keystore location
    private String keystore;

    // Module signer's keystore type
    private String storetype;

    // Time Stamping Authority URI
    private URI tsaURI;

    // Signed Module File (if not specified, use module file path)
    private File signedModuleFile;

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (OptionException | Command.Exception x) {
            err.println(x.getMessage());
            System.exit(1);
        }
    }

    public static void run(String[] args)
        throws OptionException, Command.Exception
    {
        new Signer().exec(args);
    }

    private Signer() { }

    private void exec(String[] args) throws OptionException, Command.Exception
    {
        parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("v", "verbose"),
                          "Enable verbose output");
        parser.acceptsAll(Arrays.asList("h", "?", "help"),
                          "Show this help message");
        parser.acceptsAll(Arrays.asList("p", "protected"),
                          "Do not prompt for a keystore password");

        OptionSpec<String> keystoreUrl
            = (parser.acceptsAll(Arrays.asList("k", "keystore"),
                                 "URL or file name of module signer's"
                                 + " keystore location")
               .withRequiredArg()
               .describedAs("location")
               .ofType(String.class));

        OptionSpec<String> keystoreType
            = (parser.acceptsAll(Arrays.asList("s", "storetype"),
                                 "Module signer's keystore type")
               .withRequiredArg()
               .describedAs("type")
               .ofType(String.class));

        OptionSpec<URI> tsa
            = (parser.acceptsAll(Arrays.asList("t", "tsa"),
                                 "URL of Time Stamping Authority")
               .withRequiredArg()
               .describedAs("location")
               .ofType(URI.class));

        OptionSpec<File> signedModule
            = (parser.acceptsAll(Arrays.asList("f", "signedmodulefile"),
                                 "File name of signed module file")
               .withRequiredArg()
               .describedAs("path")
               .ofType(File.class));

        if (args.length == 0) {
            usage();
            return;
        }

        OptionSet opts = parser.parse(args);
        if (opts.has("h")) {
            usage();
            return;
        }
        if (opts.has("v"))
            verbose = true;

        if (opts.has(keystoreUrl)) {
            keystore = opts.valueOf(keystoreUrl);
            // Compatibility with keytool and jarsigner options
            if (keystore.equals("NONE"))
                keystore = null;
        }
        if (opts.has(keystoreType))
            storetype = opts.valueOf(keystoreType);
        if (opts.has("protected"))
            protectedPath = true;
        if (opts.has(tsa))
            tsaURI = opts.valueOf(tsa);
        if (opts.has(signedModule))
            signedModuleFile = opts.valueOf(signedModule);

        new Jsign().run(null, opts);
    }

    private void usage() {
        out.format("%n");
        out.format("usage: jsign [-v] [--keystore <keystore-location>] "
                   + "[--storetype <keystore-type>] [--protected] "
                   + "[--tsa <url>] [--signedmodulefile <signed-module-file>] "
                   + "<module-file> <signer-alias>%n");
        out.format("%n");
        try {
            parser.printHelpOn(out);
        } catch (IOException x) {
            throw new AssertionError(x);
        }
        out.format("%n");
    }

    class Jsign extends Command<SimpleLibrary> {
        protected void go(SimpleLibrary lib)
            throws Command.Exception
        {
            String moduleFile = command;
            String signer = takeArg();
            if (verbose)
                out.println("Signing module using '" + signer + "' from "
                            + " keystore " + keystore);

            SignerParameters params = null;

            File tmpFile = (signedModuleFile == null)
                ? new File(moduleFile + ".sig") : signedModuleFile;

            // First, read in module file and calculate hashes
            List<byte[]> hashes = null;
            byte[] moduleInfoBytes = null;
            try (FileInputStream mfis = new FileInputStream(moduleFile);
                 Reader reader = new Reader(new DataInputStream(mfis)))
            {
                params = createSignerParameters(signer);
                moduleInfoBytes = reader.readStart();
                if (reader.hasSignature())
                    throw new Command.Exception("module file is already signed");
                reader.readRest();
                hashes = reader.getCalculatedHashes();
            } catch (IOException | GeneralSecurityException x) {
                throw new Command.Exception(x);
            }

            // Next, generate signature and insert into signed module file
            try (RandomAccessFile mraf = new RandomAccessFile(moduleFile, "r");
                 RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw"))
            {   
                raf.setLength(0);

                // Transfer header and module-info from module file
                // to signed module file.
                long remainderStart = ModuleFileFormat.FILE_HEADER_LENGTH
                                      + ModuleFileFormat.SECTION_HEADER_LENGTH
                                      + moduleInfoBytes.length;
                FileChannel source = mraf.getChannel();
                FileChannel dest = raf.getChannel();
                for (long pos = 0; pos < remainderStart;) {
                    pos += source.transferTo(pos, remainderStart - pos, dest);
                }

                // Write out the Signature Section
                writeSignatureSection(raf, hashes, params);

                // Transfer the remainder of the file
                for (long pos = remainderStart; pos < mraf.length();) {
                    pos += source.transferTo(pos, mraf.length() - pos, dest);
                }

            } catch (IOException | GeneralSecurityException x) {
                try {
                    Files.deleteIfExists(tmpFile.toPath());
                } catch (IOException ioe) {
                    if (verbose)
                        err.println(x);
                    throw new Command.Exception(ioe);
                }
                throw new Command.Exception(x);
            }

            if (signedModuleFile == null) {
                try {
                    Files.move(tmpFile.toPath(), new File(moduleFile).toPath(),
                               StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ioe) {
                    throw new Command.Exception(ioe);
                }
            }
        }

        private SignerParameters createSignerParameters(String signer)
            throws IOException, GeneralSecurityException
        {
            PasswordProtection storePassword = null;
            PasswordProtection keyPassword = null;
            if (keystore == null)
                keystore = System.getProperty("user.home")
                           + File.separator + ".keystore";

            try (FileInputStream inStream = new FileInputStream(keystore)) {

                if (storetype == null)
                    storetype = KeyStore.getDefaultType();

                KeyStore ks = KeyStore.getInstance(storetype);

                // Prompt user for the keystore password (except when
                // prohibited or when using Windows MY native keystore)
                if (!protectedPath || !isWindowsKeyStore(storetype)) {
                    err.print("Enter password for " + storetype
                              + " keystore: ");
                    err.flush();
                    storePassword
                        = new PasswordProtection(Password.readPassword(in));
                }

                // Load the keystore
                ks.load(inStream, storePassword.getPassword());

                if (!ks.containsAlias(signer))
                    throw new KeyStoreException("Signer alias " + signer
                                                + "does not exist");

                if (!ks.entryInstanceOf(signer, PrivateKeyEntry.class))
                    throw new KeyStoreException("Signer alias " + signer
                                                + "is not a private key");

                // First try to recover the key using keystore password
                PrivateKeyEntry pke = null;
                try {
                    pke = (PrivateKeyEntry)ks.getEntry(signer, storePassword);
                } catch (UnrecoverableKeyException e) {
                    if (protectedPath ||
                        storetype.equalsIgnoreCase("PKCS11") ||
                        storetype.equalsIgnoreCase("Windows-MY")) {
                        throw e;
                    }
                    // Otherwise prompt the user for key password
                    err.print("Enter password for '" + signer + "' key: ");
                    err.flush();
                    keyPassword = 
                        new PasswordProtection(Password.readPassword(in));
                    pke = (PrivateKeyEntry)ks.getEntry(signer, keyPassword);
                }

                // Create the signing mechanism
                PrivateKey privateKey = pke.getPrivateKey();
                Signature signature = Signature.getInstance(
                                    getSignatureAlgorithm(privateKey));
                signature.initSign(privateKey);

                X509Certificate[] signerChain
                    = (X509Certificate[])pke.getCertificateChain();
                return new SignerParameters(signature, signerChain, tsaURI);
            } finally {
                try {
                    if (storePassword != null) {
                        storePassword.destroy();
                    }
                    if (keyPassword != null) {
                        keyPassword.destroy();
                    }
                } catch (DestroyFailedException x) {
                    if (verbose)
                        err.println("Could not destroy password: " + x);
                }
            }
        }

        /*
         * The signature algorithm is derived from the signer key.
         */
        private String getSignatureAlgorithm(PrivateKey privateKey)
            throws SignatureException {
            switch (privateKey.getAlgorithm()) {
                case "RSA":
                    return "SHA256withRSA";
                case "DSA":
                    return "SHA256withDSA";
                case "EC":
                    return "SHA256withECDSA";
            }
            throw new SignatureException(privateKey.getAlgorithm()
                                         + " private keys are not supported");
        }

        /*
         * Generates the module file signature and writes the Signature Section.
         *
         * The data to be signed is a list of hash values:
         *
         *     ToBeSignedContent {
         *         u2 moduleHeaderHashLength;
         *         b* moduleHeaderHash;
         *         u2 moduleInfoHashLength;
         *         b* moduleInfoHash;
         *         u2 sectionHashLength;
         *         b* sectionHash;
         *         ...
         *         // other section hashes (in same order as module file)
         *         ...
         *         u2 moduleFileHashLength;
         *         b* moduleFileHash;
         *     }
         *
         */
        private void writeSignatureSection(DataOutput out,
                                           List<byte[]> hashes,
                                           SignerParameters params)
            throws IOException, SignatureException, NoSuchAlgorithmException {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            short hashLength;
            for (byte[] hash : hashes) {
                hashLength = (short) hash.length;
                baos.write((byte) ((hashLength >>> 8) & 0xFF));
                baos.write((byte) ((hashLength >>> 0) & 0xFF));
                baos.write(hash, 0, hashLength);
            }
            byte[] toBeSigned = baos.toByteArray();

            // Compute the signature
            SignedModule.PKCS7Signer signer = new SignedModule.PKCS7Signer();
            byte[] signature = signer.generateSignature(toBeSigned, params);

            // Generate the hash for the signature header and content
            baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            short signatureType = (short)signer.getSignatureType().value();
            dos.writeShort(signatureType);
            short signatureLength = (short)signature.length;
            dos.writeInt(signature.length);
            byte[] signatureHeader = baos.toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(signatureHeader);
            md.update(signature);
            byte[] hash = md.digest();

            // Write out the Signature Section
            new SectionHeader(FileConstants.ModuleFile.SectionType.SIGNATURE,
                              FileConstants.ModuleFile.Compressor.NONE,
                              signature.length + 6,
                              (short)0, hash).write(out);
            out.write(signatureHeader);
            out.write(signature);
        }
    }

    /**
     * Returns true if KeyStore has a password. This is true except for
     * MSCAPI KeyStores
     */
    private static boolean isWindowsKeyStore(String storetype) {
        return storetype.equalsIgnoreCase("Windows-MY")
                || storetype.equalsIgnoreCase("Windows-ROOT");
    }
}
