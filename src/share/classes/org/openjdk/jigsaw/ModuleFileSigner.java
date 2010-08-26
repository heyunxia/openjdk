/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.cert.Certificate;

import static org.openjdk.jigsaw.FileConstants.*;

/**
 * Generate a digital signature for a module file.
 */
public interface ModuleFileSigner {

    /**
     * Gets the signature format that is supported by this signer.
     *
     * @return The supported signature type
     *
     * @see FileConstants.ModuleFile.SignatureType
     */
    public ModuleFile.SignatureType getSignatureType();

    /**
     * Generates the digital signature.
     *
     * @param toBeSigned The data that will be signed
     * @param parameters The parameters used to control signing
     *
     * @return The raw signature bytes
     *
     * @throws SignatureException If an error occurs during signing
     */
    public byte[] generateSignature(byte[] toBeSigned,
                                    ModuleFileSigner.Parameters parameters)
        throws SignatureException;

    /**
     * An extensible collection of parameters used during signing.
     */
    public interface Parameters {
        /**
         * Returns the signature algorithm used to generate the signature.
         */
        public Signature getSignatureAlgorithm();

        /**
         * Returns the signer's chain of public key certificates.
         */
        public Certificate[] getSignerCertificateChain();
    }
}
