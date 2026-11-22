/*
 * Drishti Engine · engines by contract, proven with a JDK only.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.engine.host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

/**
 * The class loader of one engine JAR (section 17.3): platform packages from the host, always (parent-first); everything else from the
 * JAR and the JDK only (child-only). No class of the host, of a library the host happens to ship, of another engine or plug-in is
 * visible. Engines of one JAR share one loader. It separates names; it is not a security boundary. The kit, the command line and every
 * host use this one class, so a JAR that loads in the kit loads in a host.
 */
public final class EngineJarLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ClassLoader host;
    private final Path jar;

    /** A loader for {@code jar} whose platform classes come from {@code host} (the loader that holds engine-api and the family libraries). */
    public EngineJarLoader(Path jar, ClassLoader host) {
        super("engine:" + jar.getFileName(), new URL[] {url(jar)}, ClassLoader.getPlatformClassLoader());
        this.host = host;
        this.jar = jar;
    }

    private static URL url(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The JAR this loader reads. */
    public Path jar() {
        return jar;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (Platform.isPlatform(name)) {
            if (!Platform.isShared(name)) {
                throw new ClassNotFoundException(name + " is the host's own and is not handed to an engine");
            }
            return host.loadClass(name);                       // the host's copy, whatever the JAR holds
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = getParent().loadClass(name);           // the JDK
                } catch (ClassNotFoundException notJdk) {
                    c = findClass(name);                       // the JAR, or not found
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    public URL getResource(String name) {
        String dotted = name.replace('/', '.');
        if (Platform.isPlatform(dotted)) {
            return Platform.isShared(dotted) ? host.getResource(name) : null;
        }
        URL u = getParent().getResource(name);
        return u != null ? u : findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        String dotted = name.replace('/', '.');
        if (Platform.isPlatform(dotted)) {
            return Platform.isShared(dotted) ? host.getResources(name) : Collections.emptyEnumeration();
        }
        return findResources(name);
    }
}
