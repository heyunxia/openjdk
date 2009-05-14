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

/* @test
 * @summary org.openjdk.jigsaw.Resolver unit test
 * @compile _Resolver.java MockLibrary.java ModuleInfoBuilder.java
 *          ConfigurationBuilder.java
 * @run main _Resolver
 */

import java.io.*;
import java.util.*;
import java.lang.module.*;
import org.openjdk.jigsaw.*;

import static java.lang.System.out;
import static java.lang.module.Dependence.Modifier;


public class _Resolver {

    private static JigsawModuleSystem jms = JigsawModuleSystem.instance();

    private static Configuration go(Library lib, String root)
	throws ConfigurationException
    {
	try {
	    return (Resolver
		    .create(lib, jms.parseModuleIdQuery(root))
		    .run());
	} catch (IOException x) {
	    throw new Error("Unexpected I/O exception", x);
	}
    }

    private static void fail(Library lib, String root)
	throws Exception
    {
	try {
	    go(lib, root);
	} catch (ConfigurationException x) {
	    out.format("Failed as expected: %s%n", x.getMessage());
	    return;
	}
	throw new Exception("FAIL: Configuration succeeded");
    }

    private static ModuleInfoBuilder module(String id) {
	return ModuleInfoBuilder.module(id);
    }

    private static void dump(Configuration cf) {
	cf.dump(System.out);
    }


    private static int testsRun = 0;
    private static int failures = 0;

    private static void fail(String fmt, Object ... args) {
	out.format("FAIL: " + fmt + "%n", args);
	failures++;
    }

    private static List<Test> tests = new ArrayList<Test>();

    private static abstract class Test {

	final String name;
	final boolean expectedToPass;

	Test(String n, boolean p) {
	    name = n;
	    expectedToPass = p;
	    tests.add(this);
	}

	abstract String init(MockLibrary mlib);

	void ref(ConfigurationBuilder cb) { }

	ContextBuilder context(String ... mids) {
	    return ContextBuilder.context(mids);
	}

	void run() {
	    testsRun++;
	    MockLibrary mlib = new MockLibrary();
	    String root = init(mlib);
	    if (expectedToPass) {
		try {
		    Configuration cf = go(mlib, root);
		    ConfigurationBuilder cfbd
			= ConfigurationBuilder.config(root);
		    ref(cfbd);
		    if (!cfbd.isEmpty()) {
			Configuration rcf = cfbd.build();
			if (!cf.equals(rcf)) {
			    fail("Configuration mismatch!");
			    out.format("-- Expected:%n");
			    rcf.dump(out);
			    out.format("-- Returned:%n");
			    cf.dump(out);
			    return;
			}
		    }
		    dump(cf);
		} catch (ConfigurationException x) {
		    fail("Unexpected exception: %s", x.getMessage());
		    return;
		}
	    } else {
		try {
		    go(mlib, root);
		} catch (ConfigurationException x) {
		    out.format("Failed as expected: %s%n", x.getMessage());
		    return;
		}
		fail("Configuration succeeded");
	    }
	}

    }


    // -- Tests --

    static {

        new Test("trivial", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requires("y@1"))
		    .add(module("y@1"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("y@1"));
	    }
	};

	new Test("trivialLocal", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requiresLocal("y@1"))
		    .add(module("y@1").permits("x"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1", "y@1"));
	    }
	};

        new Test("local-left", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("ll@1").requiresLocal("lc@1"))
		    .add(module("lc@1").permits("ll").permits("lr"))
		    .add(module("lr@1").requiresLocal("lc@1"))
		    .add(module("x@1").requires("ll@1"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("ll@1", "lc@1"));
	    }
	};

        new Test("local-left-right", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("ll@1").requiresLocal("lc@1"))
		    .add(module("lc@1").permits("ll").permits("lr"))
		    .add(module("lr@1").requiresLocal("lc@1"))
		    .add(module("x@1").requires("ll@1").requires("lr@1"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("ll@1", "lc@1", "lr@1"));
	    }
	};

        new Test("local-x", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("ll@1").requiresLocal("lc@1"))
		    .add(module("lc@1").permits("ll").permits("lr")
			 .requiresLocal("lx@1"))
		    .add(module("lr@1").requiresLocal("lc@1"))
		    .add(module("lx@1").permits("lc"))
		    .add(module("x@1").requires("ll@1").requires("lr@1"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("ll@1", "lc@1", "lr@1", "lx@1"));
	    }
	};

        new Test("diamond", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requires("y@2").requires("w@4"))
		    .add(module("y@2").requires("z@>=3"))
		    .add(module("z@9"))
		    .add(module("z@4"))
		    .add(module("z@3"))
		    .add(module("w@4").requires("z@<=4"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("y@2"))
		    .add(context("z@4"))
		    .add(context("w@4"));
	    }
	};

        new Test("diamond-fail", false) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requires("y@2").requires("w@4"))
		    .add(module("y@2").requires("z@<=3"))
		    .add(module("z@4"))
		    .add(module("z@3"))
		    .add(module("z@9"))
		    .add(module("w@4").requires("z@>=4"));
		return "x@1";
	    }
	};

	new Test("simple", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requiresPublic("y@1"))
		    .add(module("y@1"))
		    .addPublic("x@1", "x.A")
		    .addOther("x@1", "x.B")
		    .addPublic("y@1", "y.C")
		    .addOther("y@1", "y.D");
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1")
			 .local("x", "x.A").local("x", "x.B")
			 .remote("y", "+y"))
		    .add(context("y@1")
			 .local("y", "y.D").local("y", "y.C"));
	    }
	};


        new Test("publicity", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requires("y@1").requires("v@1"))
		    .add(module("y@1").requiresPublic("z@1").requires("w@1"))
		    .add(module("z@1"))
		    .add(module("w@1"))
		    .add(module("v@1"))
		    .addPublic("x@1", "x.P")
		    .addOther("x@1", "x.O")
		    .addPublic("y@1", "y.P")
		    .addOther("y@1", "y.O")
		    .addPublic("z@1", "z.P")
		    .addOther("z@1", "z.O")
		    .addPublic("w@1", "w.P")
		    .addOther("w@1", "w.O")
		    .addPublic("v@1", "v.P")
		    .addOther("v@1", "v.O");
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("z@1").local("z", "z.P").local("z", "z.O"))
		    .add(context("y@1").local("y", "y.P").local("y", "y.O")
			 .remote("w", "+w").remote("z", "+z"))
		    .add(context("x@1").local("x", "x.O").local("x", "x.P")
			 .remote("v", "+v").remote("z", "+z").remote("y", "+y"))
		    .add(context("w@1").local("w", "w.P").local("w", "w.O"))
		    .add(context("v@1").local("v", "v.O").local("v", "v.P"));
	    }
	};

        new Test("dup", false) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requires("y@1").requires("z@1"))
		    .add(module("y@1"))
		    .add(module("z@1"))
		    .addPublic("y@1", "a.B")
		    .addPublic("z@1", "a.B");
		return "x@1";
	    }
	};

	/* ## Not yet
        new Test("optional-satisfied", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requiresOptional("y@1"))
		    .add(module("y@1"))
		    .addPublic("y@1", "y.Z");
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"))
		    .add(context("y@1"));
	    }
	};

        new Test("optional-unsatisfied", true) {
	    String init(MockLibrary mlib) {
		mlib.add(module("x@1").requiresOptional("y@1"));
		return "x@1";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(context("x@1"));
	    }
	};
	*/

	/*
        new Test("template", true) {
	    String init(MockLibrary mlib) {
		return "root";
	    }
	    void ref(ConfigurationBuilder cfbd) {
		cfbd.add(...);
	    }
	};
	*/

    }

    public static void main(String[] args) throws Exception {
        System.setProperty("org.openjdk.jigsaw.noPlatformDefault", "#t");
	for (Test t : tests) {
	    out.format("%n-- %s%n", t.name);
	    t.run();
	}
	out.format("%n== %d test%s, %d failure%s%n",
		   testsRun, testsRun != 1 ? "s": "",
		   failures, failures != 1 ? "s" : "");
    }

}
