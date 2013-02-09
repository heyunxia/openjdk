/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic test for PersistentTreeMap
 */

import java.io.File;
import java.io.IOException;
import org.openjdk.jigsaw.PersistentTreeMap;
import org.openjdk.jigsaw.PersistentTreeMap.StringAndInt;


public class Basic {

    static final boolean debug = true;
    static int fail = 0;
    static String filename = "basic.db";

    public static void main(String[] args) throws IOException {
        debug("Using database: " + filename + "\n");
        File dbFile = new File(filename);
        test(dbFile);
    }

    static void test(File dbFile) throws IOException {
        // test put(String,String), get(String)
        try (PersistentTreeMap pmap = PersistentTreeMap.create(dbFile)) {
            // verify null for non existent key
            if (get("non_existent_key", pmap) != null)
                throw new RuntimeException("Failed: key:" + "non_existent_key" +
                                           ", expected: " + null);

            put("helloThereKey", "helloThereValue", pmap);
            check("helloThereKey", "helloThereValue", pmap);
            put("chegar.Key", "chegar.Value", pmap);
            check("chegar.Key", "chegar.Value", pmap);
            put("foo.bar.com", "123456789", pmap);
            check("foo.bar.com", "123456789", pmap);
            put("emptyKey",  "", pmap);
            check("emptyKey", "", pmap);

            // multiple put/gets
            put("testKeyA1", "testKeyA1Value", pmap);
            put("testKeyA2", "testKeyA2Value", pmap);
            put("testKeyA3", "testKeyA3Value", pmap);
            put("testKeyA4", "testKeyA4Value", pmap);
            put("testKeyA5", "testKeyA5Value", pmap);
            put("testKeyA6", "testKeyA6Value", pmap);
            put("testKeyA7", "testKeyA7Value", pmap);
            // duplicate key
            put("testKeyA7", "duplicate", pmap);
            check("testKeyA3", "testKeyA3Value", pmap);
            check("testKeyA4", "testKeyA4Value", pmap);
            check("testKeyA5", "testKeyA5Value", pmap);
            check("testKeyA6", "testKeyA6Value", pmap);
            check("testKeyA7", "duplicate", pmap);
            check("testKeyA2", "testKeyA2Value", pmap);
            check("testKeyA1", "testKeyA1Value", pmap);
        }

        // verify persistence
        try (PersistentTreeMap pmap = PersistentTreeMap.open(dbFile)) {
            // verify null for non existent key
            if (get("non_existent_key", pmap) != null)
                throw new RuntimeException("Failed: key:" + "non_existent_key" +
                                           ", expected: " + null);

            check("helloThereKey", "helloThereValue", pmap);
            check("chegar.Key", "chegar.Value", pmap);
            check("foo.bar.com", "123456789", pmap);
            check("emptyKey", "", pmap);
            check("testKeyA3", "testKeyA3Value", pmap);
            check("testKeyA4", "testKeyA4Value", pmap);
            check("testKeyA5", "testKeyA5Value", pmap);
            check("testKeyA6", "testKeyA6Value", pmap);
            check("testKeyA7", "duplicate", pmap);
            check("testKeyA2", "testKeyA2Value", pmap);
            check("testKeyA1", "testKeyA1Value", pmap);
        }

        // test put(String,int), getInt(String)
        try (PersistentTreeMap pmap = PersistentTreeMap.create(dbFile)) {
            // verify -1 for non existent key
            check("non_existent_key", -1, pmap);

            put("smallKey", 5, pmap);
            check("smallKey", 5, pmap);
            put("big.Key", 12345678, pmap);
            check("big.Key", 12345678, pmap);
            put("t.i.n.y.K.e.y", 0, pmap);
            check("t.i.n.y.K.e.y", 0, pmap);

            // multiple put/gets
            put("testIntKeyA1", 34567, pmap);
            put("testIntKeyA2", 34568, pmap);
            put("testIntKeyA3", 34569, pmap);
            put("testIntKeyA4", 34570, pmap);
            put("testIntKeyA5", 34571, pmap);
            put("testIntKeyA6", 34572, pmap);
            put("testIntKeyA7", 98765, pmap);
            //duplicate
            put("testIntKeyA7", 34573, pmap);
            check("testIntKeyA3", 34569, pmap);
            check("testIntKeyA4", 34570, pmap);
            check("testIntKeyA5", 34571, pmap);
            check("testIntKeyA6", 34572, pmap);
            check("testIntKeyA7", 34573, pmap);
            check("testIntKeyA2", 34568, pmap);
            check("testIntKeyA1", 34567, pmap);
        }

        // verify persistence
        try (PersistentTreeMap pmap = PersistentTreeMap.open(dbFile)) {
            // verify -1 for non existent key
            check("non_existent_key", -1, pmap);

            check("testIntKeyA3", 34569, pmap);
            check("testIntKeyA4", 34570, pmap);
            check("testIntKeyA5", 34571, pmap);
            check("testIntKeyA6", 34572, pmap);
            check("testIntKeyA7", 34573, pmap);
            check("testIntKeyA2", 34568, pmap);
            check("testIntKeyA1", 34567, pmap);
            check("smallKey", 5, pmap);
            check("big.Key", 12345678, pmap);
            check("t.i.n.y.K.e.y", 0, pmap);
        }

        // test put(String,String,int), getStringAndInt(String)
        try (PersistentTreeMap pmap = PersistentTreeMap.create(dbFile)) {
            // verify null for non existent key
            if (pmap.getStringAndInt("non_existent_key") != null)
                throw new RuntimeException("Failed: key:" + "non_existent_key" +
                                           ", expected: " + null);

            put("stringAndIntKey", "stringAndIntValue", 5, pmap);
            check("stringAndIntKey", "stringAndIntValue", 5, pmap);
            put("hello.There.Key", "helloThereValue", 4, pmap);
            check("hello.There.Key", "helloThereValue", 4, pmap);
            put("chegar.Key", "chegar.Value", 56, pmap);
            check("chegar.Key", "chegar.Value", 56, pmap);
            put("foo.bar.com", "123456789", 987654321, pmap);
            check("foo.bar.com", "123456789", 987654321, pmap);
            put("e.m.p.t.y.K.e.y",  "", 78, pmap);
            check("e.m.p.t.y.K.e.y", "", 78, pmap);

            // multiple put/gets
            put("testKeyA1", "testKeyA1Value", 45, pmap);
            put("testKeyA2", "testKeyA2Value", 46, pmap);
            put("testKeyA3", "testKeyA3Value", 47, pmap);
            put("testKeyA4", "testKeyA4Value", 48, pmap);
            put("testKeyA5", "testKeyA5Value", 49, pmap);
            put("testKeyA6", "testKeyA6Value", 50, pmap);
            put("testKeyA7", "testKeyA7Value", 51, pmap);
            //duplicate
            put("testKeyA7",  "duplicate", 15, pmap);
            check("testKeyA3", "testKeyA3Value", 47, pmap);
            check("testKeyA4", "testKeyA4Value", 48, pmap);
            check("testKeyA5", "testKeyA5Value", 49, pmap);
            check("testKeyA6", "testKeyA6Value", 50, pmap);
            check("testKeyA7", "duplicate", 15, pmap);
            check("testKeyA2", "testKeyA2Value", 46, pmap);
            check("testKeyA1", "testKeyA1Value", 45, pmap);
        }

        // verify persistence, read only (open)
        try (PersistentTreeMap pmap = PersistentTreeMap.open(dbFile)) {
            // verify null for non existent key
            if (pmap.getStringAndInt("non_existent_key") != null)
                throw new RuntimeException("Failed: key:" + "non_existent_key" +
                                           ", expected: " + null);

            check("stringAndIntKey", "stringAndIntValue", 5, pmap);
            check("hello.There.Key", "helloThereValue", 4, pmap);
            check("chegar.Key", "chegar.Value", 56, pmap);
            check("foo.bar.com", "123456789", 987654321, pmap);
            check("e.m.p.t.y.K.e.y", "", 78, pmap);
            check("testKeyA3", "testKeyA3Value", 47, pmap);
            check("testKeyA4", "testKeyA4Value", 48, pmap);
            check("testKeyA5", "testKeyA5Value", 49, pmap);
            check("testKeyA6", "testKeyA6Value", 50, pmap);
            check("testKeyA7", "duplicate", 15, pmap);
            check("testKeyA2", "testKeyA2Value", 46, pmap);
            check("testKeyA1", "testKeyA1Value", 45, pmap);

            // Read only
            checkThrow("anyKey", "anyValue", pmap);
            checkThrow("anyKey", 56, pmap);
            checkThrow("anyKey", "anyValue", 56, pmap);
        }

        if (fail > 0)
            throw new RuntimeException("Failed: " + fail + " tests failed. " +
                                       "Check output");
    }

    static void put(String key, String value, PersistentTreeMap pmap)
        throws IOException
    {
        debug("putting: " + key + ", " + value + "\n");
        pmap.put(key, value);
    }

    static void put(String key, int value, PersistentTreeMap pmap)
        throws IOException
    {
        debug("putting: " + key + ", " + value + "\n");
        pmap.put(key, value);
    }

    static void put(String key, String sval, int ival, PersistentTreeMap pmap)
        throws IOException
    {
        debug("putting: " + key + ", [" + sval + "," + ival + "]" + "\n");
        pmap.put(key, sval, ival);
    }

    static String get(String key, PersistentTreeMap pmap)
        throws IOException
    {
        debug("getting: " + key + ", ");
        String value =  pmap.get(key);
        debug(value + "\n");
        return value;
    }

    static int getInt(String key, PersistentTreeMap pmap)
        throws IOException
    {
        debug("getting: " + key + ", ");
        int value =  pmap.getInt(key);
        debug(value + "\n");
        return value;
    }

    static StringAndInt getStringAndInt(String key, PersistentTreeMap pmap)
        throws IOException
    {
        debug("getting: " + key + ", ");
        StringAndInt value =  pmap.getStringAndInt(key);
        debug("[" + value.s + "," + value.i + "]" + "\n");
        return value;
    }

    static void check(String key, String expected, PersistentTreeMap pmap)
        throws IOException
    {
        String value = get(key, pmap);
        if (!expected.equals(value)) {
            fail("Failed: key:" + key + ", expected: " +
                 expected + ", got:" + value);
        }
    }

    static void check(String key, int expected, PersistentTreeMap pmap)
        throws IOException
    {
        int value = getInt(key, pmap);
        if (expected != value) {
            fail("Failed: key:" + key + ", expected: " +
                 expected + ", got:" + value);
        }
    }

    static void check(String key, String expectedString,
                      int expectedInt, PersistentTreeMap pmap)
        throws IOException
    {
        StringAndInt value = getStringAndInt(key, pmap);
        if (!expectedString.equals(value.s) || expectedInt != value.i) {
            fail("Failed: key:" + key + ", expected: " +
                 "[" + expectedString + "," + expectedInt + "]" + ", got:" +
                 "[" + value.s + "," + value.i + "]");
        }
    }

    static void checkThrow(String key, String value, PersistentTreeMap pmap)
        throws IOException
    {
        try {
            pmap.put(key, value);
            fail("Failed: expected throw when putting to read only database");
        } catch (IOException e) {
            debug("[expected] caught " + e + "\n");
        }
    }

    static void checkThrow(String key, int value, PersistentTreeMap pmap)
        throws IOException
    {
        try {
            pmap.put(key, value);
            fail("Failed: expected throw when putting to read only database");
        } catch (IOException e) {
            debug("[expected] caught " + e + "\n");
        }
    }

    static void checkThrow(String key, String sval,
                           int ival, PersistentTreeMap pmap)
        throws IOException
    {
        try {
            pmap.put(key, sval, ival);
            fail("Failed: expected throw when putting to read only database");
        } catch (IOException e) {
            debug("[expected] caught " + e + "\n");
        }
    }

    static void debug(String message) {
        if (debug)
            System.out.print(message);
    }

    static void fail(String message) {
        System.err.println(message);
        fail++;
        //Thread.dumpStack();
    }
}
