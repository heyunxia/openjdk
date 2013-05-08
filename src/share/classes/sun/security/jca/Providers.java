/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jca;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.security.ProviderException;
import java.util.*;
import org.openjdk.jigsaw.Platform;
import sun.security.util.Debug;

/**
 * Collection of methods to get and set provider list. Also includes
 * special code for the provider list during JAR verification.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
public class Providers {

    private static final ThreadLocal<ProviderList> threadLists =
        new InheritableThreadLocal<>();

    // number of threads currently using thread-local provider lists
    // tracked to allow an optimization if == 0
    private static volatile int threadListsUsed;

    // current system-wide provider list
    // Note volatile immutable object, so no synchronization needed.
    private static volatile ProviderList providerList;

    static {
        // set providerList to empty list first in case initialization somehow
        // triggers a getInstance() call (although that should not happen)
        providerList = ProviderList.EMPTY;
        providerList = ProviderList.fromSecurityProperties();
    }

    private Providers() {
        // empty
    }

    // we need special handling to resolve circularities when loading
    // signed JAR files during startup. The code below is part of that.

    // Basically, before we load data from a signed JAR file, we parse
    // the PKCS#7 file and verify the signature. We need a
    // CertificateFactory, Signatures, etc. to do that. We have to make
    // sure that we do not try to load the implementation from the JAR
    // file we are just verifying.
    //
    // To avoid that, we use different provider settings during JAR
    // verification.  However, we do not want those provider settings to
    // interfere with other parts of the system. Therefore, we make them local
    // to the Thread executing the JAR verification code.
    //
    // The code here is used by sun.security.util.SignatureFileVerifier.
    // See there for details.

    private static final String BACKUP_PROVIDER_CLASSNAME =
        "sun.security.provider.VerificationProvider";

    // Hardcoded classnames of providers to use for JAR verification.
    // MUST NOT be on the bootclasspath and not in signed JAR files.
    private static final String[] jarVerificationProviders = {
        "sun.security.provider.Sun",
        "sun.security.rsa.SunRsaSign",
        // Note: SunEC *is* in a signed JAR file, but it's not signed
        // by EC itself. So it's still safe to be listed here.
        "sun.security.ec.SunEC",
        BACKUP_PROVIDER_CLASSNAME,
    };

    // Return to Sun provider or its backup.
    // This method should only be called by
    // sun.security.util.ManifestEntryVerifier and java.security.SecureRandom.
    public static Provider getSunProvider() {
        try {
            Class<?> clazz = Class.forName(jarVerificationProviders[0]);
            return (Provider)clazz.newInstance();
        } catch (Exception e) {
            try {
                Class<?> clazz = Class.forName(BACKUP_PROVIDER_CLASSNAME);
                return (Provider)clazz.newInstance();
            } catch (Exception ee) {
                throw new RuntimeException("Sun provider not found", e);
            }
        }
    }

    /**
     * Start JAR verification. This sets a special provider list for
     * the current thread. You MUST save the return value from this
     * method and you MUST call stopJarVerification() with that object
     * once you are done.
     */
    public static Object startJarVerification() {
        ProviderList currentList = getProviderList();
        ProviderList jarList = currentList.getJarList(jarVerificationProviders);
        // return the old thread-local provider list, usually null
        return beginThreadProviderList(jarList);
    }

    /**
     * Stop JAR verification. Call once you have completed JAR verification.
     */
    public static void stopJarVerification(Object obj) {
        // restore old thread-local provider list
        endThreadProviderList((ProviderList)obj);
    }

    /**
     * Return the current ProviderList. If the thread-local list is set,
     * it is returned. Otherwise, the system wide list is returned.
     */
    public static ProviderList getProviderList() {
        ProviderList list = getThreadProviderList();
        if (list == null) {
            list = getSystemProviderList();
        }
        return list;
    }

    /**
     * Set the current ProviderList. Affects the thread-local list if set,
     * otherwise the system wide list.
     */
    public static void setProviderList(ProviderList newList) {
        if (getThreadProviderList() == null) {
            setSystemProviderList(newList);
        } else {
            changeThreadProviderList(newList);
        }
    }

    /**
     * Get the full provider list with invalid providers (those that
     * could not be loaded) removed. This is the list we need to
     * present to applications.
     */
    public static ProviderList getFullProviderList() {
        ProviderList list;
        synchronized (Providers.class) {
            list = getThreadProviderList();
            if (list != null) {
                ProviderList newList = list.removeInvalid();
                if (newList != list) {
                    changeThreadProviderList(newList);
                    list = newList;
                }
                return list;
            }
        }
        list = getSystemProviderList();
        ProviderList newList = list.removeInvalid();
        if (newList != list) {
            setSystemProviderList(newList);
            list = newList;
        }
        return list;
    }

    private static ProviderList getSystemProviderList() {
        return providerList;
    }

    private static void setSystemProviderList(ProviderList list) {
        providerList = list;
    }

    public static ProviderList getThreadProviderList() {
        // avoid accessing the threadlocal if none are currently in use
        // (first use of ThreadLocal.get() for a Thread allocates a Map)
        if (threadListsUsed == 0) {
            return null;
        }
        return threadLists.get();
    }

    // Change the thread local provider list. Use only if the current thread
    // is already using a thread local list and you want to change it in place.
    // In other cases, use the begin/endThreadProviderList() methods.
    private static void changeThreadProviderList(ProviderList list) {
        threadLists.set(list);
    }

    /**
     * Methods to manipulate the thread local provider list. It is for use by
     * JAR verification (see above) and the SunJSSE FIPS mode only.
     *
     * It should be used as follows:
     *
     *   ProviderList list = ...;
     *   ProviderList oldList = Providers.beginThreadProviderList(list);
     *   try {
     *     // code that needs thread local provider list
     *   } finally {
     *     Providers.endThreadProviderList(oldList);
     *   }
     *
     */

    public static synchronized ProviderList beginThreadProviderList(ProviderList list) {
        if (ProviderList.debug != null) {
            ProviderList.debug.println("ThreadLocal providers: " + list);
        }
        ProviderList oldList = threadLists.get();
        threadListsUsed++;
        threadLists.set(list);
        return oldList;
    }

    public static synchronized void endThreadProviderList(ProviderList list) {
        if (list == null) {
            if (ProviderList.debug != null) {
                ProviderList.debug.println("Disabling ThreadLocal providers");
            }
            threadLists.remove();
        } else {
            if (ProviderList.debug != null) {
                ProviderList.debug.println
                    ("Restoring previous ThreadLocal providers: " + list);
            }
            threadLists.set(list);
        }
        threadListsUsed--;
    }

    // ProviderLoader used to find Providers in classpath or module mode
    private static class ProviderLoaderHolder {
        private static final ProviderLoader pl
            = Platform.isModuleMode() ? new ModuleProviderLoader()
                                      : new ClassPathProviderLoader();
    }

    // Returns the current ProviderLoader
    static ProviderLoader getProviderLoader() {
        return ProviderLoaderHolder.pl;
    }

    /**
     * A provider loading interface.
     */
    static interface ProviderLoader {
        /**
         * Loads the provider with the specified classname.
         *
         * @param  className the classname of the provider
         * @return the Provider, or null if it cannot be found or loaded
         * @throws ProviderException if the Provider's constructor
         *         throws a ProviderException
         * @throws UnsupportedOperationException if the Provider's constructor
         *         throws an UnsupportedOperationException
         */
        Provider load(String className);

        /**
         * Loads the provider with the specified classname and argument.
         *
         * @param  className the classname of the provider
         * @param  argument a String arg passed to the provider's constructor
         * @return the Provider, or null if it cannot be found or loaded
         * @throws ProviderException if the Provider's constructor
         *         throws a ProviderException
         * @throws UnsupportedOperationException if the Provider's constructor
         *         throws an UnsupportedOperationException
         */
        Provider load(String className, String argument);
    }

    private static class ClassPathProviderLoader implements ProviderLoader {
        private final static Debug debug =
            Debug.getInstance("jca", "ClassPathProviderLoader");

        // parameters for the Provider(String) constructor
        private final static Class[] CL_STRING = { String.class };

        public Provider load(String className) {
            return doLoad(className, null);
        }

        public Provider load(String className, String argument) {
            return doLoad(className, argument);
        }

        private Provider doLoad(String className, String argument) {
            try {
                Class<?> provClass = getClass(className);
                if (argument != null) {
                    Constructor<?> cons = provClass.getConstructor(CL_STRING);
                    return asProvider(cons.newInstance(argument));
                } else {
                    return asProvider(provClass.newInstance());
                }
            } catch (Exception e) {
                Throwable t;
                if (e instanceof InvocationTargetException) {
                    t = ((InvocationTargetException)e).getCause();
                } else {
                    t = e;
                }
                if (debug != null) {
                    String info = (argument != null)
                                  ? className + "('" + argument + "')"
                                  : className;
                    debug.println("Error loading provider " + info);
                    t.printStackTrace(System.err);
                }
                // provider indicates fatal error, pass through exception
                if (t instanceof ProviderException ||
                    t instanceof UnsupportedOperationException) {
                    throw (RuntimeException)t;
                }
                return null;
            }
        }

        private Class<?> getClass(String name) throws ClassNotFoundException {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            return (cl != null) ? cl.loadClass(name) : Class.forName(name);
        }

        private Provider asProvider(Object obj) {
            return (obj instanceof Provider) ? (Provider)obj : null;
        }
    }

    private static class ModuleProviderLoader implements ProviderLoader {
        private final static Debug debug =
            Debug.getInstance("jca", "ModuleProviderLoader");
        private final Map<String, Provider> providers;
        private final Map<String, RuntimeException> causes;

        ModuleProviderLoader() {
            providers = new HashMap<>();
            causes = new HashMap<>();
            ServiceLoader<Provider> sl = ServiceLoader.load(Provider.class);
            Iterator<Provider> services = sl.iterator();
            while (services.hasNext()) {
                try {
                    Provider p = services.next();
                    providers.put(p.getClass().getName(), p);
                } catch (ServiceConfigurationError sce) {
                    // log error
                    if (debug != null) {
                        debug.println("Error loading provider");
                        sce.printStackTrace(System.err);
                    }
                    Throwable cause = sce.getCause();
                    if (cause != null) {
                        Throwable t;
                        if (cause instanceof InvocationTargetException) {
                            t = ((InvocationTargetException)cause).getCause();
                        } else {
                            t = cause;
                        }
                        // if cause is ProviderException or UOE, save it
                        // for later comparison in Provider.load
                        if (t instanceof ProviderException ||
                            t instanceof UnsupportedOperationException)
                        {
                            causes.put(sce.getMessage(), (RuntimeException)t);
                        }
                    }
                }
            }
        }

        // ## Optional constructor arguments are not supported by ServiceLoader.
        // ## For now, we just ignore the argument.
        public Provider load(String className, String argument) {
            return load(className);
        }

        public Provider load(String className) {
            Provider p = providers.get(className);
            if (p != null) {
                return p;
            } else {
                // ## This checks if the provider's className is embedded inside
                // ## the ServiceConfigurationError message. This is a hack,
                // ## and is not guaranteed to work in the future or on all
                // ## implementations. Ideally, we should add a method to get
                // ## the service's class name from ServiceConfigurationError.
                for (String msg : causes.keySet()) {
                    if (msg.indexOf(className) != -1) {
                        throw causes.get(msg);
                    }
                }
            }
            return null;
        }
    }
}
