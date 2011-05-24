/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.*;
import java.security.cert.*;
import java.util.Collection;
import java.util.Set;

import static org.openjdk.jigsaw.FileConstants.*;

/**
 * Validate the digital signature of a module file.
 */
public interface ModuleFileVerifier {
    /**
     * Gets the signature format that is supported by this verifier.
     *
     * @return The supported signature type
     *
     * @see FileConstants.ModuleFile.SignatureType
     */
    public ModuleFile.SignatureType getSignatureType();

    /**
     * Verifies the module file signature and performs certificate path
     * validation for each of the signers.
     *
     * @param parameters The parameters used to control verification
     *
     * @return The signers which have been successfully verified (never null
     *    or empty)
     *
     * @throws SignatureException If the signature or certificate chain cannot 
     *    be verified or an error occurs during verification
     */
    public Set<CodeSigner> verifySignature(ModuleFileVerifier.Parameters
                                           parameters)
        throws SignatureException;

    /**
     * Checks that the module file hashes carried in signature match the
     * hashes generated from the actual contents of the module file.
     *
     * @param parameters The parameters used to control verification
     *
     * @throws SignatureException If a file hash fails to match or an error
     *    occurs during verification
     */
    public void verifyHashes(ModuleFileVerifier.Parameters parameters)
        throws SignatureException;

    /**
     * Checks that the module file hashes carried in the signature match the
     * hashes generated from the actual contents of the module file. This
     * method verifies the module header hash and the module info hash 
     * calculated by the ModuleFileReader.readStart method.
     *
     * @param parameters The parameters used to control verification
     *
     * @throws SignatureException If a file hash fails to match or an error
     *   occurs during verification
     */
    public void verifyHashesStart(ModuleFileVerifier.Parameters parameters)
        throws SignatureException;

    /**
     * Checks that the module file hashes carried in the signature match the
     * hashes generated from the actual contents of the module file. This
     * method verifies each of the section hashes (except the module info hash)
     * and the whole file hash calculated by the ModuleFileReader.readRest
     * method.
     *
     * @param parameters The parameters used to control verification
     *
     * @throws SignatureException If a file hash fails to match or an error
     *   occurs during verification
     */
    public void verifyHashesRest(ModuleFileVerifier.Parameters parameters)
        throws SignatureException;

    /**
     * An extensible collection of parameters used during signature
     * verification.
     */
    public interface Parameters {
        /**
         * Returns a collection of the most-trusted public key certificates.
         */
        public Collection<X509Certificate> getTrustedCerts();
    }
}
