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
 * @run main Basher
 * @run main Basher -noshare
 * @summary Tests multiple readers for PersistentTreeMap
 */

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Phaser;
import org.openjdk.jigsaw.PersistentTreeMap;
import org.openjdk.jigsaw.PersistentTreeMap.StringAndInt;


public class Basher implements Runnable {

    // Small values so that the test can be run in constrained
    // test environments. Increase when running manually.
    static final int NUM_THREADS = 50;
    static final int NUM_ENTRIES = 10000;

    static final boolean debug = false;
    static final String FILENAME = "basher.db";
    static volatile int fail;

    static enum Mode {
        /** Share the PersistentTreeMap instance across multiple threads */
        SHARE_INSTANCE,
        /** Create a per thread PersistentTreeMap instance */
        NOSHARE_INSTANCE
    };
    static Mode mode;

    final PersistentTreeMap pmap;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && args[0].equals("-noshare")) {
            mode = Mode.NOSHARE_INSTANCE;
            System.out.println("Using NO share mode");
        } else {
            mode = Mode.SHARE_INSTANCE;
            System.out.println("Using share mode");
        }
        test();
    }

    Basher(PersistentTreeMap pmap) {
        this.pmap = pmap;
    }

    static Basher createBasher(PersistentTreeMap ptm) throws Exception {
        if (mode.equals(Mode.NOSHARE_INSTANCE))
            return new Basher(PersistentTreeMap.open(new File(FILENAME)));
        else if (mode.equals(Mode.SHARE_INSTANCE))
            return new Basher(ptm);
        else
            throw new Error("Unknown run mode");
    }

    static void test() throws Exception {
        System.out.print("Using " + NUM_THREADS + " threads, and " +
                         NUM_ENTRIES + " entries\n");
        debug("Using database: " + FILENAME + "\n");
        File dbFile = new File(FILENAME);

        System.out.print("creating db...");
        createdb(dbFile);
        System.out.print("completed\n");

        try (PersistentTreeMap pmap = PersistentTreeMap.open(dbFile)) {
            Thread[] threads = new Thread[NUM_THREADS];
            for (int i=0; i<NUM_THREADS; i++) {
                threads[i] = new Thread(createBasher(pmap));
            }
            for (int i=0; i<NUM_THREADS; i++) {
                threads[i].start();
            }
            for (int i=0; i<NUM_THREADS; i++) {
                threads[i].join();
            }
        }

        if (fail > 0)
            throw new RuntimeException("Failed: " + fail + " tests failed. " +
                                       "Check output");
    }

    static void createdb(File dbFile) throws IOException {
        String tn = Thread.currentThread().getName();

        try (PersistentTreeMap cpmap = PersistentTreeMap.create(dbFile)) {
            for (int i=0; i<NUM_ENTRIES; i++) {
                put("Key" + i, "Value" + i, cpmap, tn);
                put("Int.Key" + i, i, cpmap, tn);
                put("S.A.I.Key" + i, "S.A.I.Value" + i, i, cpmap, tn );
            }
        }
    }

    @Override
    public void run() {
        String tn = Thread.currentThread().getName();

        toTheStartingGate();
        try {
            for (int i=0; i< NUM_ENTRIES; i++) {
                check("Key" + i, "Value" + i, pmap, tn);
                check("Int.Key" + i, i, pmap, tn);
                check("S.A.I.Key" + i, "S.A.I.Value" + i, i, pmap, tn);
                if (i % 1000 == 0)
                    System.out.print(".");
            }
        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
            if (mode.equals(Mode.NOSHARE_INSTANCE))
                try {pmap.close(); } catch (IOException e) { fail(e.getMessage()); }
        }
    }

    // Mechanism to get all test threads into "running" mode.
    static Phaser atTheStartingGate = new Phaser(NUM_THREADS);
    static void toTheStartingGate() {
        atTheStartingGate.arriveAndAwaitAdvance();
     }

    static void put(String key, String value, PersistentTreeMap pmap, String tn)
        throws IOException
    {
        debug(tn + ": putting: " + key + ", " + value + "\n");
        pmap.put(key, value);
    }

    static void put(String key, int value, PersistentTreeMap pmap, String tn)
        throws IOException
    {
        debug(tn + ": putting: " + key + ", " + value + "\n");
        pmap.put(key, value);
    }

    static void put(String key, String sval, int ival,
                    PersistentTreeMap pmap, String tn)
        throws IOException
    {
        debug(tn + ": putting: " + key + ", [" + sval + "," + ival + "]" + "\n");
        pmap.put(key, sval, ival);
    }

    static String get(String key, PersistentTreeMap pmap, String tn)
        throws IOException
    {
        debug(tn + ": getting: " + key + ", ");
        String value =  pmap.get(key);
        debug(value + "\n");
        return value;
    }

    static int getInt(String key, PersistentTreeMap pmap, String tn)
        throws IOException
    {
        debug(tn + ": getting: " + key + ", ");
        int value =  pmap.getInt(key);
        debug(value + "\n");
        return value;
    }

    static StringAndInt getStringAndInt(String key, PersistentTreeMap pmap,
                                         String tn)
        throws IOException
    {
        debug(tn + ": getting: " + key + ", ");
        StringAndInt value =  pmap.getStringAndInt(key);
        debug("[" + value.s + "," + value.i + "]" + "\n");
        return value;
    }

    static void check(String key, String expected, PersistentTreeMap pmap,
                      String tn)
        throws IOException
    {
        String value = get(key, pmap, tn);
        if (!expected.equals(value))
            fail(tn + ": Failed: key:" + key + ", expected: " +
                 expected + ", got:" + value);
    }

    static void check(String key, int expected, PersistentTreeMap pmap,
                      String tn)
        throws IOException
    {
        int value = getInt(key, pmap, tn);
        if (expected != value)
            fail(tn + ": Failed: key:" + key + ", expected: " +
                 expected + ", got:" + value);
    }

    static void check(String key, String expectedString,
                      int expectedInt, PersistentTreeMap pmap, String tn)
        throws IOException
    {
        StringAndInt value = getStringAndInt(key, pmap, tn);
        if (!expectedString.equals(value.s) || expectedInt != value.i) {
            fail(tn + ": Failed: key:" + key + ", expected: " +
                 "[" + expectedString + "," + expectedInt + "]" + ", got:" +
                 "[" + value.s + "," + value.i + "]");
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
