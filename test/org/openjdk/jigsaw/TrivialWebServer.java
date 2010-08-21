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

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;

import static java.lang.System.out;
import static java.lang.System.err;
import static java.net.HttpURLConnection.*;


public class TrivialWebServer {

    private boolean debug = System.getenv("TWS_DEBUG") != null;

    private final PrintStream log;
    private final Handler handler;

    private TrivialWebServer(Path rpath, PrintStream l) {
        log = l;
        handler = new Handler(rpath);
    }

    private void dump(String t, Headers hs) {
        log.format("%s headers%n", t);
        for (Map.Entry<String,List<String>> e : hs.entrySet()) {
            log.format("  %s : %s%n", e.getKey(), e.getValue());
        }
    }

    private void copy(Path p, OutputStream out)
        throws IOException
    {
        byte[] buf = new byte[8192];
        InputStream in = p.newInputStream();
        try {
            int n;
            while ((n = in.read(buf)) > 0)
                out.write(buf, 0, n);
        } finally {
            in.close();
        }
    }

    private static final SimpleDateFormat HTTP_DATE;

    static {
        HTTP_DATE = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                                           Locale.US);
        HTTP_DATE.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private class Handler
        implements HttpHandler
    {

        private URI root;
        private URI BARE_ROOT = URI.create("/");

        Handler(Path rpath) {
            root = rpath.toAbsolutePath().toUri().normalize();
            if (debug)
                log.format("root %s%n", root);
        }

        private void notFound(HttpExchange hx, URI hxu)
            throws IOException
        {
            byte[] err
                = ("<b>Not found: " + hxu + "</b>").getBytes("ASCII");
            hx.sendResponseHeaders(HTTP_NOT_FOUND, err.length);
            OutputStream os = hx.getResponseBody();
            os.write(err);
        }

        private String etag(Object ob) {
            if (ob == null)
                return null;
            return '"' + Integer.toHexString(ob.hashCode()) + '"';
        }

        public void handle(HttpExchange hx) throws IOException {
            try {

                URI hxu = hx.getRequestURI();
                URI u = root.resolve(BARE_ROOT.relativize(hxu));
                Path p = Paths.get(u);
                if (debug) {
                    log.format("%s --> %s%n", hxu, p);
                    dump("req", hx.getRequestHeaders());
                }
                if (!p.exists()) {
                    notFound(hx, hxu);
                    return;
                }
                BasicFileAttributes ba
                    = Attributes.readBasicFileAttributes(p);

                // Directory -> index.html
                //
                if (ba.isDirectory()) {
                    String us = hxu.toString();
                    if (!us.endsWith("/")) {
                        Headers ahs = hx.getResponseHeaders();
                        ahs.put("Location", Arrays.asList(us + "/"));
                        hx.sendResponseHeaders(HTTP_MOVED_PERM, -1);
                        return;
                    }
                    p = p.resolve("index.html");
                    if (!p.exists()) {
                        notFound(hx, hxu);
                        return;
                    }
                    ba = Attributes.readBasicFileAttributes(p);
                }
                if (debug)
                    log.format("%s --> %s%n", hxu, p);

                // Check Last-Modified/ETag headers
                //
                long mtime = ba.lastModifiedTime().toMillis();
                String etag = etag(ba.fileKey());
                Headers rhs = hx.getRequestHeaders();
                String rmtime = rhs.getFirst("If-Modified-Since");
                boolean condget = false;
                boolean sendit = false;
                if (rmtime != null) {
                    condget = true;
                    long rmt = HTTP_DATE.parse(rmtime).getTime();
                    sendit = mtime > rmt;
                }
                String retag = rhs.getFirst("If-None-Match");
                boolean tagChanged = true;
                if (retag != null) {
                    condget = true;
                    sendit = sendit || !retag.equals(etag);
                }
                if (condget && !sendit) {
                    hx.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
                    return;
                }

                // Send content
                //
                Headers ahs = hx.getResponseHeaders();
                ahs.set("Content-Type", "application/octet-stream");
                ahs.set("Last-Modified",
                        HTTP_DATE.format(new Date(mtime)));
                if (etag != null)
                    ahs.set("ETag", etag);
                if (debug)
                    dump("ans", hx.getResponseHeaders());
                hx.sendResponseHeaders(HTTP_OK, ba.size());
                copy(p, hx.getResponseBody());

            } catch (Exception x) {
                x.printStackTrace(out);
            } finally {
                hx.close();
            }
        }

    }

    private HttpServer server = null;

    private void bind(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 10);
        server.createContext("/", handler);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    private void start() throws IOException {
        server.start();
    }

    public void stop() throws IOException {
        server.stop(0);
        ((ExecutorService)server.getExecutor()).shutdown();
    }

    public int port() {
        if (server == null)
            throw new IllegalStateException();
        return server.getAddress().getPort();
    }

    public static TrivialWebServer create(Path root, PrintStream log)
        throws IOException
    {
        TrivialWebServer tws = new TrivialWebServer(root, log);
        Random r = new Random();
        for (;;) {
            int p = r.nextInt((1 << 16) - 1024) + 1024;
            try {
                tws.bind(p);
                break;
            } catch (BindException x) {
                continue;
            }
        }
        tws.start();
        return tws;
    }

    public static TrivialWebServer create(Path root, int port, PrintStream log)
        throws IOException
    {
        if (port == -1)
            return create(root, log);
        TrivialWebServer tws = new TrivialWebServer(root, log);
        tws.bind(port);
        tws.start();
        return tws;
    }

    public static void main(String[] args) throws IOException {
        int port = 8081;
        Path root = Paths.get(".");
        Iterator<String> ai = Arrays.asList(args).iterator();
        while (ai.hasNext()) {
            String a = ai.next();
            if (a.matches("\\d+")) {
                port = Integer.parseInt(a);
                continue;
            }
            if (a.equals("-r")) {
                port = -1;
                continue;
            }
            root = Paths.get(a);
        }
        TrivialWebServer tws
            = TrivialWebServer.create(root, port, System.out);
        System.out.format("Serving %s on port %d%n", root, tws.port());
    }

}
