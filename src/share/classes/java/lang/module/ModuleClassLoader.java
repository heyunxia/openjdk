/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.lang.reflect.Module;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import sun.reflect.ReflectionFactory;

public abstract class ModuleClassLoader
    extends SecureClassLoader
{

    private ModuleSystem moduleSystem;

    protected ModuleClassLoader(ModuleSystem ms) {
        super(null);
        moduleSystem = ms;
    }

    protected Class<?> defineClass(Module m, String name,
                                   byte[] b, int off, int len)
        throws ClassFormatError
    {
        Class<?> c = super.defineClass(name, b, off, len, m.getCodeSource());
        sun.misc.SharedSecrets.getJavaLangAccess().setModule(c, m);
        return c;
    }

    static final ReflectionFactory reflectionFactory =
        AccessController.doPrivileged(
            new sun.reflect.ReflectionFactory.GetReflectionFactoryAction());

    // Invokes a corresponding native method implemented in the VM ## ??
    protected Module defineModule(ModuleId id, byte[] bs,
                                  int off, int len, CodeSource cs)
        throws ClassFormatError
    {
        if (off != 0 || bs.length != len) // ##
            throw new IllegalArgumentException();
        ModuleInfo mi = moduleSystem.parseModuleInfo(bs);
        return reflectionFactory.newModule(mi, this, cs);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        // ## Handle compatibility cases here
        throw new ClassNotFoundException(name);
    }

    /**
     * Tests if a {@linkplain java.lang.reflect.Module module}
     * of the given name is present in this module loader's context.
     *
     * @param mn a module's name
     * @return {@code true} if the module of the given name is present;
     *         {@code false} otherwise.
     */
    public abstract boolean isModulePresent(String mn);

    /**
     * Checks if a {@linkplain java.lang.reflect.Module module}
     * of the given name is present in this module loader's context.
     *
     * @param mn a module's name
     * @throws ModuleNotPresentException if the module of the given name
     *         is not present in this module class loader's context.
     */
    public void requireModulePresent(String mn) {
        if (!isModulePresent(mn)) {
            throw new ModuleNotPresentException("module " + mn + " not present");
        }
    }

}
