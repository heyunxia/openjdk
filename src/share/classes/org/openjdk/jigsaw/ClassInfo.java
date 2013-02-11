/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Arrays;

import static java.lang.System.out;

/**
 * <p> A specialized class-file reader which just reads a class file's type
 * name and module metadata </p>
 *
 * <p> This class uses NIO, and so is not suitable for use during
 * bootstrap. </p>
 */

public class ClassInfo {

    private String name;
    private String moduleName;
    private String moduleVersion;
    private boolean isModuleInfo;
    private int acc;

    public String name() { return name; }
    public String moduleName() { return moduleName; }
    public String moduleVersion() { return moduleVersion; }
    public boolean isModuleInfo() { return isModuleInfo; }
    public boolean isPublic() { return (acc & ACC_PUBLIC) != 0; }

    public String toString() {
        return String.format("%s[%s %s@%s]",
                             this.getClass().getName(),
                             name, moduleName, moduleVersion);
    }

    // cf. java.io.DataInputStream
    private static String readUTF(ByteBuffer bb)
        throws UTFDataFormatException
    {
        int utflen = bb.getShort() & 0xffff;
        int start = bb.position();
        int count = 0;
        char[] chararr = new char[utflen];
        int chararr_count = 0;
        int c, char2, char3;

        while (count < utflen) {
            c = bb.get() & 0xff;
            if (c > 127) {
                bb.position(bb.position() - 1);
                break;
            }
            count++;
            chararr[chararr_count++]=(char)c;
        }

        while (count < utflen) {
            c = bb.get() & 0xff;
            switch (c >> 4) {
                case 0: case 1: case 2: case 3:
                case 4: case 5: case 6: case 7:
                    /* 0xxxxxxx*/
                    count++;
                    chararr[chararr_count++]=(char)c;
                    break;
                case 12: case 13:
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    char2 = bb.get() & 0xff;
                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException(
                            "malformed input around byte " + count);
                    chararr[chararr_count++]=(char)(((c & 0x1F) << 6) |
                                                    (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    char2 = bb.get() & 0xff;
                    char3 = bb.get() & 0xff;
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new UTFDataFormatException(
                            "malformed input around byte " + (count-1));
                    chararr[chararr_count++]=(char)(((c     & 0x0F) << 12) |
                                                    ((char2 & 0x3F) << 6)  |
                                                    ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException(
                        "malformed input around byte " + count);
            }
        }

        if (count > utflen)
            throw new UTFDataFormatException("malformed input:"
                                             + " partial character at end");

        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararr_count);

    }

    private final static int ACC_PUBLIC = 0x0001;
    private final static int ACC_FINAL = 0x0010;
    private final static int ACC_SUPER = 0x0020;
    private final static int ACC_INTERFACE = 0x0200;
    private final static int ACC_ABSTRACT = 0x0400;
    private final static int ACC_MODULE = 0x8000;

    private final static int CONSTANT_Class = 7;
    private final static int CONSTANT_Fieldref = 9;
    private final static int CONSTANT_Methodref = 10;
    private final static int CONSTANT_InterfaceMethodref = 11;
    private final static int CONSTANT_String = 8;
    private final static int CONSTANT_Integer = 3;
    private final static int CONSTANT_Float = 4;
    private final static int CONSTANT_Long = 5;
    private final static int CONSTANT_Double = 6;
    private final static int CONSTANT_NameAndType = 12;
    private final static int CONSTANT_Utf8 = 1;
    private final static int CONSTANT_MethodHandle = 15;
    private final static int CONSTANT_MethodType = 16;
    private final static int CONSTANT_InvokeDynamic = 18;
    private final static int CONSTANT_ModuleId = 19;
    private final static int CONSTANT_ModuleQuery = 20;

    private Object[] constantPool;

    private void readConstantPool(ByteBuffer bb, int cpcount)
        throws IOException
    {
        Object[] cp = new Object[cpcount + 1];
        constantPool = cp;
        for (int i = 1; i < cpcount; i++) {
            int tag = bb.get() & 0xff;
            switch (tag) {
            case CONSTANT_Class:
                cp[i] = bb.getShort() & 0xffff;
                break;
            case CONSTANT_Fieldref:
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
                bb.getShort();
                bb.getShort();
                break;
            case CONSTANT_String:
                bb.getShort();
                break;
            case CONSTANT_Integer:
            case CONSTANT_Float:
                bb.getInt();
                break;
            case CONSTANT_Long:
            case CONSTANT_Double:
                bb.getLong();
                i++;
                break;
            case CONSTANT_NameAndType:
                bb.getShort();
                bb.getShort();
                break;
            case CONSTANT_InvokeDynamic:
                bb.getShort();
                bb.getShort();
                break;
            case CONSTANT_MethodHandle:
                bb.get();
                bb.getShort();
                break;
            case CONSTANT_MethodType:
                bb.getShort();
                break;
            case CONSTANT_ModuleId:
            case CONSTANT_ModuleQuery:
                int ni = bb.getShort() & 0xffff;
                int vi = bb.getShort() & 0xffff;
                cp[i] = new int[] { ni, vi };
                break;
            case CONSTANT_Utf8:
                cp[i] = readUTF(bb);
                break;
            default:
                throw new ClassFormatError("Unknown constant-pool tag " + tag);
            }
        }
    }

    private int constantInt(int i) {
        if (i > 0 && i < constantPool.length) {
            Object ob = constantPool[i];
            if (ob instanceof Integer)
                return ((Integer)ob).intValue();
        }
        throw new ClassFormatError();
    }

    private int[] constantIntPair(int i) {
        if (i > 0 && i < constantPool.length) {
            Object ob = constantPool[i];
            if (ob.getClass().getName().equals("[I")) {
                int[] ia = (int[])ob;
                if (ia.length == 2)
                    return ia;
            }
        }
        throw new ClassFormatError();
    }

    private String constantString(int i) {
        if (i > 0 && i < constantPool.length) {
            Object ob = constantPool[i];
            if (ob instanceof String)
                return (String)ob;
        }
        throw new ClassFormatError();
    }

    private static void skip(ByteBuffer bb, int n)
        throws IOException
    {
        bb.position(bb.position() + n);
    }

    private void load(ByteBuffer bb, String path)
        throws IOException
    {

        int magic = bb.getInt();
        if (magic != 0xcafebabe)
            throw new ClassFormatError(path);
        int minor = bb.getShort() & 0xffff;
        int major = bb.getShort() & 0xffff;
        int cpcount = bb.getShort() & 0xffff;
        readConstantPool(bb, cpcount);
        acc = bb.getShort() & 0xffff;

        int this_class = bb.getShort() & 0xffff;
        name = constantString(constantInt(this_class)).replace('/', '.');
        isModuleInfo = name.endsWith(".module-info");

        int super_class = bb.getShort() & 0xffff;
        int icount = bb.getShort() & 0xffff;
        skip(bb, icount * 2);

        int fcount = bb.getShort() & 0xffff;
        for (int i = 0; i < fcount; i++) {
            bb.getShort();
            bb.getShort();
            bb.getShort();
            int ac = bb.getShort() & 0xffff;
            for (int j = 0; j < ac; j++) {
                bb.getShort();
                int n = bb.getInt();
                skip(bb, n);
            }
        }

        int mcount = bb.getShort() & 0xffff;
        for (int i = 0; i < mcount; i++) {
            bb.getShort();
            bb.getShort();
            bb.getShort();
            int ac = bb.getShort() & 0xffff;
            for (int j = 0; j < ac; j++) {
                bb.getShort();
                int n = bb.getInt();
                skip(bb, n);
            }
        }

        int ac = bb.getShort() & 0xffff;
        for (int i = 0; i < ac; i++) {
            int name = bb.getShort() & 0xffff;
            if (constantString(name).equals("Module")) {
                if (bb.getInt() != 2)
                    throw new ClassFormatError(path
                                               + ": Invalid module attribute");
                int mid = bb.getShort() & 0xffff;
                int[] midp = constantIntPair(mid);
                moduleName = constantString(midp[0]).replace('/', '.');
                int vi = midp[1];
                if (vi != 0)
                    moduleVersion = constantString(vi);
            } else {
                int n = bb.getInt();
                skip(bb, n);
            }
        }

        constantPool = null;

    }

    private void load(File f)
        throws IOException
    {
        ByteBuffer bb = null;
        FileInputStream fin = new FileInputStream(f);
        try {
            FileChannel fc = fin.getChannel();
            bb = ByteBuffer.allocate((int)(fc.size() & 0xffffffff));
            while (bb.hasRemaining()) {
                if (fc.read(bb) == -1)
                    throw new EOFException();
            }
        } finally {
            fin.close();
        }
        bb.flip();
        load(bb, f.getPath());
    }

    private static byte[] readAllBytes(InputStream in, int initialSize)
        throws IOException
    {
        int capacity = (initialSize > 0) ? initialSize : 8192;
        byte[] buf = new byte[capacity];
        int nread = 0;
        int rem = buf.length;
        int n;
        // read to EOF which may read more or less than initialSize
        while ((n = in.read(buf, nread, rem)) > 0) {
            nread += n;
            rem -= n;
            assert rem >= 0;
            if (rem == 0) {
                // need larger buffer
                int newCapacity = capacity << 1;
                if (newCapacity < 0) {
                    if (capacity == Integer.MAX_VALUE)
                        throw new OutOfMemoryError("Required array size too large");
                    newCapacity = Integer.MAX_VALUE;
                }
                rem = newCapacity - capacity;
                buf = Arrays.copyOf(buf, newCapacity);
                capacity = newCapacity;
            }
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

    private void load(InputStream in, int size, String path)
        throws IOException
    {
        try (InputStream source = in) {
            byte[] buf = readAllBytes(in, size);
            load(ByteBuffer.wrap(buf, 0, buf.length), path);
        }
    }

    // -- Entry points --

    public static ClassInfo read(File f)
        throws IOException
    {
        ClassInfo ci = new ClassInfo();
        try {
            ci.load(f);
        } catch (BufferUnderflowException x) {
            throw new ClassFormatError(f.toString());
        }
        return ci;
    }

    public static ClassInfo read(InputStream in, long size, String path)
        throws IOException
    {
        assert size > 1 && size <= Integer.MAX_VALUE
            : path + " size = " + size;
        ClassInfo ci = new ClassInfo();
        try {
            ci.load(in, (int)size, path);
        } catch (BufferUnderflowException x) {
            throw new ClassFormatError(path);
        }
        return ci;
    }

    // ## Test
    //
    public static void main(String[] args)
        throws IOException
    {
        for (String a : args) {
            ClassInfo ci = read(new File(a));
            out.format("%s: %s (%s @ %s)%n",
                       a, ci.name(), ci.moduleName(), ci.moduleVersion());
        }
    }

}
