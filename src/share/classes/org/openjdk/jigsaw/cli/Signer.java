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

package org.openjdk.jigsaw.cli;

import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.security.auth.DestroyFailedException;

import static java.lang.System.err;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.security.KeyStore.PasswordProtection;
import static java.security.KeyStore.PrivateKeyEntry;
import java.util.Map;

import org.openjdk.jigsaw.*;
import org.openjdk.jigsaw.ModuleFileParserException;
import org.openjdk.jigsaw.ModuleFileParser.Event;
import org.openjdk.internal.joptsimple.OptionException;
import org.openjdk.internal.joptsimple.OptionParser;
import org.openjdk.internal.joptsimple.OptionSet;
import org.openjdk.internal.joptsimple.OptionSpec;

import static org.openjdk.jigsaw.ModuleFile.*;
import static org.openjdk.jigsaw.FileConstants.ModuleFile.*;

import sun.security.pkcs.PKCS7;
import sun.security.util.Password;

/* Interface:

jsign [-v] [--keystore <keystore-location>] \
      [--storetype <keystore-type>] [--protected] \
      [--tsa <url>] [--signedmodulefile <signed-module-file>] \
      <module-file> <signer-alias>

*/

public final class Signer {

    private OptionParser parser;
    private boolean verbose;

    // If true, do not prompt for a keystore password (for example, with a
    // keystore provider that is configured with its own PIN entry device).
    private boolean protectedPath;

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
        } catch (OptionException x) {
            err.println(x.getMessage());
            System.exit(1);
        } catch (Command.Exception x) {
            err.println(x.getMessage());
            x.printStackTrace();
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
                                 + " keystore")
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
        verbose = opts.has("v");
        if (opts.has(keystoreUrl)) {
            keystore = opts.valueOf(keystoreUrl);
            // NONE is for non-file based keystores, ex. PKCS11 tokens
            if (keystore.equals("NONE"))
                keystore = null;
        } else {
            // default is $HOME/.keystore
            keystore = System.getProperty("user.home")
                       + File.separator + ".keystore";
        }
        storetype = opts.has(keystoreType) ? opts.valueOf(keystoreType)
                                           : KeyStore.getDefaultType();
        protectedPath = opts.has("protected");
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

            PrivateKeyEntry pke = null;
            try {
                pke = getPrivateKeyEntry(signer);
            } catch (GeneralSecurityException | IOException x) {
                throw new Command.Exception("unable to extract private key " +
                                            "entry from keystore", x);
            }

            // First, read in module file and get the hashes
            List<byte[]> hashes = new ArrayList<>();
            int moduleInfoLength = 0;
            try (FileInputStream mfis = new FileInputStream(moduleFile)) {
                ValidatingModuleFileParser parser =
                        ModuleFile.newValidatingParser(mfis);
                while (parser.hasNext()) {
                    Event event = parser.next();
                    if (event == Event.END_SECTION) {
                        SectionHeader header = parser.getSectionHeader();
                        if (header.getType() == SectionType.SIGNATURE)
                            throw new Command.Exception("module file is already signed");
                        if (header.getType() == SectionType.MODULE_INFO)
                            moduleInfoLength = header.getCSize();
                    }
                }
                hashes.add(parser.getHeaderHash());
                for (byte[] hash: parser.getHashes().values())
                    hashes.add(hash);  // section hashes
                hashes.add(parser.getFileHash());
            } catch (IOException | ModuleFileParserException x) {
                throw new Command.Exception("unable to read module file", x);
            }

            // Next, generate signature and insert into signed module file
            File tmpFile = (signedModuleFile == null)
                ? new File(moduleFile + ".sig") : signedModuleFile;
            try (RandomAccessFile mraf = new RandomAccessFile(moduleFile, "r");
                 RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw"))
            {
                raf.setLength(0);

                // Transfer header and module-info from module file
                // to signed module file.
                long remainderStart = ModuleFileHeader.LENGTH
                                      + SectionHeader.LENGTH
                                      + moduleInfoLength;
                FileChannel source = mraf.getChannel();
                FileChannel dest = raf.getChannel();
                for (long pos = 0; pos < remainderStart;) {
                    pos += source.transferTo(pos, remainderStart - pos, dest);
                }

                // Write out the Signature Section
                writeSignatureSection(raf, hashes, pke);

                // Transfer the remainder of the file
                for (long pos = remainderStart; pos < mraf.length();) {
                    pos += source.transferTo(pos, mraf.length() - pos, dest);
                }

            } catch (IOException | GeneralSecurityException x) {
                try {
                    Files.deleteIfExists(tmpFile.toPath());
                } catch (IOException ioe) {
                    x.addSuppressed(ioe);
                }
                throw new Command.Exception("unable to sign module", x);
            }

            if (signedModuleFile == null) {
                try {
                    Files.move(tmpFile.toPath(), new File(moduleFile).toPath(),
                               StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ioe) {
                    throw new Command.Exception("unable to sign module", ioe);
                }
            }
        }

        private PrivateKeyEntry getPrivateKeyEntry(String signer)
            throws GeneralSecurityException, IOException
        {
            PasswordProtection storePassword = null;
            PasswordProtection keyPassword = null;

            try (InputStream inStream = new FileInputStream(keystore)) {

                // Prompt user for the keystore password (except when
                // protected is true or when using Windows MY native keystore)
                if (!protectedPath || !isWindowsKeyStore(storetype)) {
                    err.print("Enter password for " + storetype
                              + " keystore: ");
                    err.flush();
                    storePassword
                        = new PasswordProtection(Password.readPassword(in));
                }

                // Load the keystore
                KeyStore ks = KeyStore.getInstance(storetype);
                ks.load(inStream, storePassword.getPassword());

                if (!ks.containsAlias(signer))
                    throw new KeyStoreException("Signer alias " + signer
                                                + "does not exist");

                if (!ks.entryInstanceOf(signer, PrivateKeyEntry.class))
                    throw new KeyStoreException("Signer alias " + signer
                                                + "is not a private key");

                // First try to recover the key using keystore password
                try {
                    return (PrivateKeyEntry)ks.getEntry(signer, storePassword);
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
                    return (PrivateKeyEntry)ks.getEntry(signer, keyPassword);
                }
            } finally {
                try {
                    if (storePassword != null) {
                        storePassword.destroy();
                    }
                } catch (DestroyFailedException x) {
                    if (verbose)
                        err.println("Could not destroy keystore password: "
                                    + x);
                }
                try {
                    if (keyPassword != null) {
                        keyPassword.destroy();
                    }
                } catch (DestroyFailedException x) {
                    if (verbose)
                        err.println("Could not destroy private key password: "
                                    + x);
                }
            }
        }

        /*
         * The signature algorithm is derived from the signer key.
         */
        private String getSignatureAlg(PrivateKey privateKey)
            throws SignatureException
        {
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
                                           PrivateKeyEntry pke)
            throws GeneralSecurityException, IOException
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            short hashLength;
            for (byte[] hash : hashes) {
                hashLength = (short)hash.length;
                baos.write((byte) ((hashLength >>> 8) & 0xFF));
                baos.write((byte) ((hashLength >>> 0) & 0xFF));
                baos.write(hash, 0, hashLength);
            }
            byte[] toBeSigned = baos.toByteArray();

            // Compute the signature
            PrivateKey privateKey = pke.getPrivateKey();
            Signature sig = Signature.getInstance(getSignatureAlg(privateKey));
            sig.initSign(privateKey);
            sig.update(toBeSigned);

            // Create the PKCS #7 signed data message
            X509Certificate[] signerChain =
                (X509Certificate[])pke.getCertificateChain();
            byte[] signedData = PKCS7.generateSignedData(sig.sign(),
                                                         signerChain,
                                                         toBeSigned,
                                                         sig.getAlgorithm(),
                                                         tsaURI);

            // Generate the hash for the signature header and content
            baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            short signatureType = (short)SignatureType.PKCS7.value();
            dos.writeShort(signatureType);
            dos.writeInt(signedData.length);
            byte[] signatureHeader = baos.toByteArray();
            MessageDigest md =
                MessageDigest.getInstance(HashType.SHA256.algorithm());
            md.update(signatureHeader);
            md.update(signedData);
            byte[] hash = md.digest();

            // Write out the Signature Section
            SectionHeader header = new SectionHeader(SectionType.SIGNATURE,
                                                     Compressor.NONE,
                                                     signedData.length + 6,
                                                     (short)0, hash);
            header.write(out);
            out.write(signatureHeader);
            out.write(signedData);
        }
    }

    /**
     * Returns true if KeyStore has a password. This is true except for
     * MSCAPI KeyStores.
     */
    private static boolean isWindowsKeyStore(String storetype) {
        return storetype.equalsIgnoreCase("Windows-MY")
                || storetype.equalsIgnoreCase("Windows-ROOT");
    }
}
