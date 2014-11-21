/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Basic test of jrt file system provider
 * @run testng Basic
 */

import java.io.InputStream;
import java.io.DataInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * Basic tests for jrt:/ file system provider.
 */

public class Basic {

    // Checks that the given FileSystem is a jrt file system.
    private void checkFileSystem(FileSystem fs) {
        assertTrue(fs.provider().getScheme().equalsIgnoreCase("jrt"));
        assertTrue(fs.isOpen());
        assertTrue(fs.isReadOnly());
        assertEquals(fs.getSeparator(), "/");

        // one root
        Iterator<Path> roots = fs.getRootDirectories().iterator();
        assertTrue(roots.next().toString().equals("/"));
        assertFalse(roots.hasNext());
    }

    @Test
    public void testGetFileSystem() {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        checkFileSystem(fs);

        // getFileSystem should return the same object each time
        assertTrue(fs == FileSystems.getFileSystem(URI.create("jrt:/")));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCloseFileSystem() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        fs.close(); // should throw UOE
    }

    @Test
    public void testNewFileSystem() throws Exception {
        FileSystem theFileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
        Map<String, ?> env = Collections.emptyMap();
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), env)) {
            checkFileSystem(fs);
            assertTrue(fs != theFileSystem);
        }
    }

    @DataProvider(name = "knownClassFiles")
    private Object[][] knownClassFiles() {
        return new Object[][] {
            { "/java.base/java/lang/Object.class" },
            { "java.base/java/lang/Object.class" },
        };
    }

    @Test(dataProvider = "knownClassFiles")
    public void testKnownClassFiles(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path classFile = fs.getPath(path);

        assertTrue(Files.isRegularFile(classFile));
        assertTrue(Files.size(classFile) > 0L);

        // check magic number
        try (InputStream in = Files.newInputStream(classFile)) {
            int magic = new DataInputStream(in).readInt();
            assertEquals(magic, 0xCAFEBABE);
        }
    }

    @DataProvider(name = "knownDirectories")
    private Object[][] knownDirectories() {
        return new Object[][] {
            { "/"                     },
            { "."                     },
            { "./"                    },
            { "/."                    },
            { "/./"                   },
            { "/java.base/.."         },
            { "/java.base/../"        },
            { "/java.base/../."       },
            { "/java.base"            },
            { "/java.base/java/lang"  },
            { "java.base/java/lang"   },
            { "/java.base/java/lang/" },
            { "java.base/java/lang/"  }
        };
    }

    @Test(dataProvider = "knownDirectories")
    public void testKnownDirectories(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path dir = fs.getPath(path);

        assertTrue(Files.isDirectory(dir));

        // directory should not be empty
        try (Stream<Path> stream = Files.list(dir)) {
            assertTrue(stream.count() > 0L);
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            assertTrue(stream.count() > 0L);
        }
    }

    @DataProvider(name = "topLevelPkgDirs")
    private Object[][] topLevelPkgDirs() {
        return new Object[][] {
            { "/java/lang" },
            { "java/lang"  },
            { "/java/util" },
            { "java/util"  },
        };
    }

    @Test(dataProvider = "topLevelPkgDirs")
    public void testNotExists(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path dir = fs.getPath(path);

        // package directories should not be there at top level
        assertTrue(Files.notExists(dir));
    }

    /**
     * Test the URI of every file in the jrt file system
     */
    @Test
    public void testToAndFromUri() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path top = fs.getPath("/");
        try (Stream<Path> stream = Files.walk(top)) {
            stream.forEach(path -> {
                URI u = path.toUri();
                assertTrue(u.getScheme().equalsIgnoreCase("jrt"));
                assertFalse(u.isOpaque());
                assertTrue(u.getAuthority() == null);
                assertEquals(u.getPath(), path.toAbsolutePath().toString());
                Path p = Paths.get(u);
                assertEquals(p, path);
            });
        }
    }

    @Test
    public void testDirectoryNames() throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path top = fs.getPath("/");
        // check that directory names do not have trailing '/' char
        try (Stream<Path> stream = Files.walk(top)) {
            stream.skip(1).filter(Files::isDirectory).forEach(path -> {
                assertFalse(path.toString().endsWith("/"));
            });
        }
    }

    @DataProvider(name = "pathPrefixs")
    private Object[][] pathPrefixes() {
        return new Object[][] {
            { "/"                       },
            { "java.base/java/lang"     },
            { "./java.base/java/lang"   },
            { "/java.base/java/lang"    },
            { "/./java.base/java/lang"  },
            { "java.base/java/lang/"    },
            { "./java.base/java/lang/"  },
            { "/./java.base/java/lang/" },
        };
    }

    @Test(dataProvider = "pathPrefixes")
    public void testParentInDirList(String dir) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path base = fs.getPath(dir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path entry: stream) {
                assertTrue( entry.getParent().equals(base) );
            }
        }
    }
}
