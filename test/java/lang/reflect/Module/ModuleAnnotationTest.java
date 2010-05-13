/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package test;

import java.lang.annotation.Annotation;
import java.lang.module.*;
import java.lang.reflect.Module;
import java.io.*;
import java.util.*;
import com.foo.ArrayTypes;
import com.foo.ArrayTypesWithDefault;
import com.foo.Stooge;
import org.bar.ScalarTypes;
import org.bar.ScalarTypesWithDefault;
import org.bar.Point;
import org.openjdk.jigsaw.*;

/*
 * @summary Test reading of annotations from the input module-info.class
 * This references test/java/lang/annotation/UnitTest.java.
 *
 */
public class ModuleAnnotationTest {
    private static JigsawModuleSystem ms = JigsawModuleSystem.instance();
    private static int failCount = 0;
    private static void fail(String test) {
        System.out.println("Failure: " + test);
        failCount++;
    }

    public static void main(String[] argv) throws Exception {
        File libPath = new File(argv[0]);
        Library lb = SimpleLibrary.open(libPath, false);

        List<ModuleId> mids = lb.findModuleIds("test.foo.bar");
        if (mids.size() != 1)
            throw new RuntimeException("Installed modules: " + mids);

        System.out.println("Installed module : " + mids);

        for (ModuleId mid : mids) {
            loadModule(lb, mid);
        }

        if (failCount > 0) {
           throw new RuntimeException("Test failed: " + failCount);
        }
    }

    static void loadModule(Library lb, ModuleId mid) throws Exception {
        ModuleInfo mi = lb.readModuleInfo(mid);
        if (mi == null)
            throw new RuntimeException(mi + ": Can't read module-info");

        System.out.format("Module %s%n", mi.toString());
        Class<?> cls = Class.forName("foo.bar.Main");
        Module module = cls.getModule();
        System.out.format("Class %s from %s%n", cls, module.getModuleId());
        checkModule(module);
    }

    static void checkModule(Module module) throws Exception {
        if (module.isAnnotationPresent(ScalarTypes.class) &&
            module.isAnnotationPresent(ArrayTypes.class)) {
            checkScalarTypes(module.getAnnotation(ScalarTypes.class), module);
            checkArrayTypes(module.getAnnotation(ArrayTypes.class), module);
        } else if (module.isAnnotationPresent(ScalarTypesWithDefault.class) &&
                   module.isAnnotationPresent(ArrayTypesWithDefault.class)) {
            checkScalarTypes(module.getAnnotation(ScalarTypesWithDefault.class), module);
            checkArrayTypes(module.getAnnotation(ArrayTypesWithDefault.class), module);
        } else {
            throw new RuntimeException("Expected annotation is missing in " + module.getModuleId());
        }
    }

    static void checkScalarTypes(ScalarTypes st, Module m) {
        if (!(st.b()    == 1            &&
              st.s()    == 2            &&
              st.i()    == 3            &&
              st.l()    == 4L           &&
              st.c()    == '5'          &&
              st.f()    == 6.0f         &&
              st.d()    == 7.0          &&
              st.bool() == true         &&
              st.str().equals("custom") &&
              st.e()    == Stooge.MOE   &&
              st.a().x() == 1 && st.a().y() == 2))
            fail(m.getModuleId() + ": unexpected ScalarTypes");

        Class<?> cls = st.cls();
        if (cls != java.util.Map.class) {
            fail(m.getModuleId() + ": ScalarTypes.cls() returns unexpected value " + cls);
        }
    }

    static void checkScalarTypes(ScalarTypesWithDefault st, Module m) {
        if (!(st.b()    == 11            &&
              st.s()    == 12            &&
              st.i()    == 13            &&
              st.l()    == 14L           &&
              st.c()    == 'V'           &&
              st.f()    == 16.0f         &&
              st.d()    == 17.0          &&
              st.bool() == false         &&
              st.str().equals("default") &&
              st.e()    == Stooge.LARRY  &&
              st.a().x() == 11 && st.a().y() == 12))
            fail(m.getModuleId() + ": unexpected ScalarTypesWithDefault");

        Class<?> cls = st.cls();
        if (cls != java.util.Deque.class) {
            fail(m.getModuleId() + ": ScalarTypesWithDefault.cls() returns unexpected value " + cls);
        }
    }

    static void checkArrayTypes(ArrayTypes at, Module m) {
        if (!(at.b()[0]    == 1            && at.b()[1]    == 2            &&
              at.s()[0]    == 2            && at.s()[1]    == 3            &&
              at.i()[0]    == 3            && at.i()[1]    == 4            &&
              at.l()[0]    == 4L           && at.l()[1]    == 5L           &&
              at.c()[0]    == '5'          && at.c()[1]    == '6'          &&
              at.f()[0]    == 6.0f         && at.f()[1]    == 7.0f         &&
              at.d()[0]    == 7.0          && at.d()[1]    == 8.0          &&
              at.bool()[0] == true         && at.bool()[1] == false        &&
              at.str()[0].equals("custom") && at.str()[1].equals("paint")  &&
              at.e()[0]    == Stooge.MOE   && at.e()[1]    == Stooge.CURLY &&
              at.a()[0].x() == 1 && at.a()[0].y() == 2 && at.a()[1].x() == 3 && at.a()[1].y() == 4 &&
              at.b().length==2    && at.s().length==2   && at.i().length==2 &&
              at.l().length==2    && at.c().length==2   && at.d().length==2 &&
              at.bool().length==2 && at.str().length==2 && at.a().length==2))
            fail(m.getModuleId() + ": unexpected ArrayTypes");

        Class<?>[] cls = at.cls();
        if (!(cls.length == 2 &&
              cls[0] == java.util.Map.class &&
              cls[1] == java.util.Set.class))
            fail(m.getModuleId() + ": ArrayTypes.cls() returns " + Arrays.toString(cls));
    }

    static void checkArrayTypes(ArrayTypesWithDefault at, Module m) {
        if (!(at.b()[0]    == 11            &&
              at.s()[0]    == 12            &&
              at.i()[0]    == 13            &&
              at.l()[0]    == 14L           &&
              at.c()[0]    == 'V'           &&
              at.f()[0]    == 16.0f         &&
              at.d()[0]    == 17.0          &&
              at.bool()[0] == false         &&
              at.str()[0].equals("default") &&
              at.e()[0]    == Stooge.LARRY  &&
              at.a()[0].x() == 11 && at.a()[0].y() == 12 &&
              at.b().length==1    && at.s().length==1   && at.i().length==1 &&
              at.l().length==1    && at.c().length==1   && at.d().length==1 &&
              at.bool().length==1 && at.str().length==1))
            fail(m.getModuleId() + ": unexpected ArrayTypesWithDefault");

        Class<?>[] cls = at.cls();
        if (!(cls.length == 2 &&
              cls[0] == java.util.Deque.class &&
              cls[1] == java.util.Queue.class))
            fail(m.getModuleId() + ": ArrayTypesWithDefault.cls() returns " + Arrays.toString(cls));
    }
}
