/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.io.*;
import java.net.URI;
import java.util.*;
import java.lang.module.*;
import java.security.CodeSigner;
import org.openjdk.jigsaw.*;

import static java.lang.module.Dependence.Modifier;


class MockLibrary
    extends Library
{

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    MockLibrary() { }

    private Map<String,List<ModuleId>> idsForName
        = new HashMap<String,List<ModuleId>>();

    private Map<ModuleId,ModuleInfo> infoForId
        = new HashMap<ModuleId,ModuleInfo>();

    MockLibrary add(ModuleInfo mi) {
        infoForId.put(mi.id(), mi);
        List<ModuleId> ls = idsForName.get(mi.id().name());
        if (ls == null) {
            ls = new ArrayList<ModuleId>();
            idsForName.put(mi.id().name(), ls);
        }
        ls.add(mi.id());
        return this;
    }

    MockLibrary add(ModuleInfoBuilder mib) {
        return add(mib.build());
    }

    private Map<ModuleId,List<String>> publicClassesForId
        = new HashMap<ModuleId,List<String>>();

    private Map<ModuleId,List<String>> otherClassesForId
        = new HashMap<ModuleId,List<String>>();

    MockLibrary add(ModuleId id, String cn,
                    Map<ModuleId,List<String>> map)
    {
        List<String> ls = map.get(id);
        if (ls == null) {
            ls = new ArrayList<String>();
            map.put(id, ls);
        }
        ls.add(cn);
        return this;
    }

    MockLibrary addPublic(String mids, String cn) {
        return add(jms.parseModuleId(mids), cn, publicClassesForId);
    }

    MockLibrary addOther(String mids, String cn) {
        return add(jms.parseModuleId(mids), cn, otherClassesForId);
    }

    public String name() { return "mock-library"; }
    public int majorVersion() { return 0; }
    public int minorVersion() { return 1; }

    public URI location() {
        throw new UnsupportedOperationException();
    }

    public void installFromManifests(Collection<Manifest> mf) {
        throw new UnsupportedOperationException();
    }

    public void install(Collection<File> mf, boolean verifySignature) {
        throw new UnsupportedOperationException();
    }

    public Resolution resolve(Collection<ModuleIdQuery> midqs) {
        throw new UnsupportedOperationException();
    }

    public void install(Resolution res, boolean verifySignature) {
        throw new UnsupportedOperationException();
    }

    public Library parent() {
        return null;
    }

    protected void gatherLocalModuleIds(String mn, Set<ModuleId> mids) {
        throw new UnsupportedOperationException();
    }

    public List<ModuleId> findModuleIds(String moduleName) {
        List<ModuleId> ls = idsForName.get(moduleName);
        if (ls == null)
            ls = Collections.emptyList();
        return ls;
    }

    public List<ModuleId> findModuleIds(ModuleIdQuery midq) {
        throw new UnsupportedOperationException();
    }

    public ModuleId findLatestModuleId(ModuleIdQuery midq) {
        throw new UnsupportedOperationException();
    }

    public ModuleInfo readLocalModuleInfo(ModuleId mid) {
        return infoForId.get(mid);
    }

    public byte[] readLocalModuleInfoBytes(ModuleId mid) {
        throw new UnsupportedOperationException();
    }

    public CodeSigner[] readLocalCodeSigners(ModuleId mid) {
        throw new UnsupportedOperationException();
    }

    public byte[] readLocalClass(ModuleId mid, String className) {
        throw new UnsupportedOperationException();
    }

    public ModuleId findModuleForClass(String className,
                                       ModuleId requestor)
        throws ClassNotFoundException
    {
        throw new UnsupportedOperationException();
    }

    public List<String> listLocalClasses(ModuleId mid, boolean all) {
        List<String> rv = new ArrayList<String>();
        List<String> pcns = publicClassesForId.get(mid);
        if (pcns != null)
            rv.addAll(pcns);
        if (all) {
            List<String> ocns = otherClassesForId.get(mid);
            if (ocns != null)
                rv.addAll(ocns);
        }
        return rv;
    }

    public Configuration<Context> readConfiguration(ModuleId mid) {
        throw new UnsupportedOperationException();
    }

    public URI findLocalResource(ModuleId mid, String name) {
        throw new UnsupportedOperationException();
    }

    public File findLocalNativeLibrary(ModuleId mid, String name) {
        throw new UnsupportedOperationException();
    }

    public File classPath(ModuleId mid) {
        throw new UnsupportedOperationException();
    }

    public RemoteRepositoryList repositoryList() throws IOException {
        return new RemoteRepositoryList() {
            public List<RemoteRepository> repositories() {
                return Collections.emptyList();
            }

            public RemoteRepository firstRepository() {
                return null;
            }

            public RemoteRepository add(URI uri, int position) {
                throw new UnsupportedOperationException();
            }

            public boolean remove(RemoteRepository rr) {
                throw new UnsupportedOperationException();
            }

            public boolean areCatalogsStale() {
                throw new UnsupportedOperationException();
            }

            public boolean updateCatalogs(boolean force) {
                throw new UnsupportedOperationException();
            }

        };
    }

}
