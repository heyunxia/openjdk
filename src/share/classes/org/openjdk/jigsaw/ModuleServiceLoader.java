/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

/**
 * A simple module mode service loading facility that is capable of lazily
 * creating, from the configuration, service instances for a given
 * service interface.
 * <p>
 * This class can only be utilized in module mode.
 * <p>
 * Unlike {@link java.util.ServiceLoader} this class does not currently cache
 * service (provider) instances on subsequent calls to {@link #iterator() }.
 */
public final class ModuleServiceLoader<S> implements Iterable<S> {
    private final Class<S> serviceInterface;

    private ModuleServiceLoader(Class<S> serviceInterface) {
        if (!Platform.isModuleMode()) {
            throw new IllegalStateException("This class can only be used in module mode");
        }

        this.serviceInterface = serviceInterface;
    }

    private class LazyLoadingIterator implements Iterator<S> {
        final Iterator<Map.Entry<ClassLoader, Set<String>>> entries;

        ClassLoader serviceProvider;

        Iterator<String> providerClassNames;

        LazyLoadingIterator(Iterator<Map.Entry<ClassLoader, Set<String>>> entries) {
            this.entries = entries;
        }

        public boolean hasNext() {
            if (providerClassNames == null || !providerClassNames.hasNext()) {
                // move onto the next loader if possible
                if (!entries.hasNext())
                    return false;

                final Map.Entry<ClassLoader, Set<String>> entry = entries.next();
                serviceProvider = entry.getKey();
                providerClassNames = entry.getValue().iterator();
                assert providerClassNames.hasNext();
            }
            return providerClassNames.hasNext();
        }

        public S next() {
            if (!hasNext())
                throw new NoSuchElementException();

            final String cn = providerClassNames.next();
            try {
                final Object serviceInstance = Class.forName(cn, true,
                        serviceProvider).newInstance();
                return serviceInterface.cast(serviceInstance);
            } catch (ClassNotFoundException x) {
                fail(serviceInterface,
                     "Provider " + cn + " not found");
            } catch (Throwable x) {
                fail(serviceInterface,
                     "Provider " + cn + " could not be instantiated: " + x,
                     x);
            }
            throw new Error();          // This cannot happen
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static void fail(Class<?> service, String msg, Throwable cause)
            throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg,
                                            cause);
    }

    private static void fail(Class<?> service, String msg)
            throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    @Override
    public Iterator<S> iterator() {
        // Pick a known module class loader
        // In module mode the system class loader will be the class loader
        // of the root module
        final Loader loader = (Loader)ClassLoader.getSystemClassLoader();
        return new LazyLoadingIterator(
                loader.findServices(serviceInterface).entrySet().iterator());
    }

    /**
     * Load the service instances for a given service interface.
     *
     * @param <S> the service interface type
     * @param serviceInterface the service interface
     * @return the module service loader from which service interfaces can be
     * be iterated over
     */
    public static <S> ModuleServiceLoader<S> load(Class<S> serviceInterface) {
        return new ModuleServiceLoader<>(serviceInterface);
    }
}

