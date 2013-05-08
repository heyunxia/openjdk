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

// Compiled and invoked by remrepo.sh

import java.io.*;
import java.util.*;
import java.lang.module.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.*;


public class InstallFromRepo {

    private static ModuleSystem ms = ModuleSystem.base();

    private static File LIB = new File("z.lib");

    static void test(int port) throws Exception {

        URI u = URI.create("http://localhost:" + port);
        Library lib = SimpleLibrary.create(LIB,
                                           Library.systemLibraryPath());
        RemoteRepositoryList rrl = lib.repositoryList();
        rrl.add(u, 0);

        Resolution res
            = lib.resolve(Arrays.asList(ms.parseModuleIdQuery("you")));
        out.format("To install: %s%n", res.modulesNeeded());
        out.format("%d bytes to download%n", res.downloadRequired());
        out.format("%d bytes to store%n", res.spaceRequired());

        lib.install(res, true);

    }

    public static void main(String[] args)
        throws Exception
    {

        _PublishedRepository.create();
        _PublishedRepository.add(args, true);
        TrivialWebServer tws
            = TrivialWebServer.create(_PublishedRepository.REPO, out);
        out.format("Port %d%n", tws.port());

        try {
            test(tws.port());
        } finally {
            tws.stop();
        }
    }

}
