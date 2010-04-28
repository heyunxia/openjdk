/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.jigsaw;

import com.sun.tools.javac.util.ListBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.module.ModuleId;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.ExtendedLocation;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import org.openjdk.jigsaw.Library;

/**
 * A location to represent a Jigsaw module in a Jigsaw module library.
 */
public class JigsawLibraryLocation implements ExtendedLocation {
    private Library library;
    private ModuleId mid;

    // Quick and dirty temporary debug printing;
    // this should all be removed prior to final integration
    boolean DEBUG = (System.getProperty("javac.debug.modules") != null);
    void DEBUG(String s) {
        if (DEBUG)
            System.err.println(s);
    }

    JigsawLibraryLocation(Library library, ModuleId mid) {
        library.getClass(); // null check
        this.library = library;
        mid.getClass(); // null check
        this.mid = mid;
    }

    public String getName() {
        return mid.toString();
    }

    public boolean isOutputLocation() {
        // only input locations supported
        return false;
    }

    public Iterable<JavaFileObject> list(String packageName, Set<Kind> kinds, boolean recurse)
            throws IOException {
        // only Kind.CLASS supported
        if (!kinds.contains(Kind.CLASS))
            return Collections.emptySet();
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();
        packageName = normalize(packageName);
        String subpackagePrefix = packageName + ".";
        for (Library l = library; l != null; l = l.parent()) {
            List<String> classes = l.listLocalClasses(mid, true);
            if (classes != null) {
                for (String cn: classes) {
                    //DEBUG("LIST raw " + cn);
                    cn = normalize(cn);
                    String pn = packagePart(cn);
                    if (pn.equals(packageName) || (recurse && pn.startsWith(subpackagePrefix))) {
                        results.add(new LibraryFileObject(library, mid, cn));
                    }
                }
            }
        }
        DEBUG("JigsawLibraryLocation:" + library + ":" + mid + ": list " + packageName + "," + kinds + "--" + (results.size() < 5 ? results : (results.size() + " classes")));
        return results;
    }

    public String inferBinaryName(JavaFileObject file) {
        if (file instanceof LibraryFileObject)
            return ((LibraryFileObject) file).className;
        else
            return null;
    }

    private static String normalize(String name) {
        return name.replace('/', '.');
    }

    private static String packagePart(String className) {
        int sep = className.lastIndexOf('.');
        return (sep == -1) ? "" : className.substring(0, sep);
    }

    private static String simpleNamePart(String className) {
        int sep = className.lastIndexOf('.');
        return (sep == -1) ? className : className.substring(sep + 1);
    }

    static class LibraryFileObject implements JavaFileObject {
        Library library;
        ModuleId mid;
        String className;

        private LibraryFileObject(Library library, ModuleId mid, String className) {
            this.library = library;
            this.mid = mid;
            this.className = className;
        }

        public Kind getKind() {
            return Kind.CLASS;
        }

        public boolean isNameCompatible(String simpleName, Kind kind) {
            return (kind == Kind.CLASS) && simpleName.equals(simpleNamePart(className));
        }

        public NestingKind getNestingKind() {
            return null;
        }

        public Modifier getAccessLevel() {
            return null;
        }

        public URI toUri() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public String getName() {
            return library.name() + ":" + mid.toString() + ":" + className;
        }

        public InputStream openInputStream() throws IOException {
            byte[] data;
            if (className.endsWith(".module-info")) // FIXME?
                data = library.readModuleInfoBytes(mid);
            else
                data = library.readClass(mid, className);
            return new ByteArrayInputStream(data);
        }

        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException();
        }

        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            throw new UnsupportedOperationException();
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            throw new UnsupportedOperationException();
        }

        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException();
        }

        public long getLastModified() {
            return 0;
        }

        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
