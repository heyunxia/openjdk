/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.types.Commandline;

/**
 * This class provides simple limited support for using jmod.
 * Only the "create" and "install" options are supported.
 */
public class JmodTask extends Task {
    /**
     * Set the task to be performed: "create" or "install".
     * If the task is "create", the library will be created if it does not
     * already exist.
     * If the task is "install" and the module is up to date in the library
     * it will not be modified.  If the module exists but is not up to date,
     * it will be deleted before jmod is invoked.
     */
    public void setTask(String task) {
        this.task = task;
    }

    /**
     * Set the library to be created/used.
     * See jmod -L/--library.
     */
    public void setLibrary(File library) {
        this.library = library;
    }

    /**
     * Set the parent of the library to be created.
     * See jmod -P/--parent-path.
     */
    public void setParent(File parent) {
        this.parent = parent;
    }

    /**
     * Set whether or not jmod should be executed in a separate process.
     */
    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Set the path for the executable for jmod, for use when fork is true.
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    /**
     * Set additional arguments for the task. If the task is "install",
     * use this to specify module package files to be installed.
     */
    public void addArg(Commandline.Argument arg) {
        args.add(arg);
    }

    @Override
    public void execute() {
        if (task == null)
            throw new BuildException("task not set");

        if (task.equals("create"))
            doCreate();
        else if (task.equals("install"))
            doInstall();
        else
            throw new BuildException("task not recognized");
    }

    void doCreate() {
        if (library.exists()) {
            if (new File(library, "%jigsaw-library").exists()) {
                log("Skipping " + library + " because it already exists",
                        Project.MSG_VERBOSE);
                return;
            } else
                throw new BuildException("library exists but is not a valid Jigsaw library");
        } else {
            File libDir = library.getParentFile();
            libDir.mkdirs();
            List<String> opts = new ArrayList<String>();
            add(opts, "create");
            add(opts, "--library", library.getPath());
            if (parent == null)
                add(opts, "--no-parent");
            else
                add(opts, "--parent-path", parent.getPath());

            System.out.println("Creating module library " + library);

            try {
                jmod(opts);
            } catch (InterruptedException e) {
                throw new BuildException("error occurred", e);
            } catch (IOException e) {
               throw new BuildException("error occurred", e);
            }
        }
    }

    void doInstall() {
        List<File> files = new ArrayList<File>();

        for (Commandline.Argument arg: args) {
            for (String s: arg.getParts()) {
                File f = new File(s);
                String fname = f.getName();
                int lastDot = fname.lastIndexOf(".");
                String fbase = (lastDot == -1 ? fname : fname.substring(0, lastDot));
                int at = fbase.indexOf("@");
                String mname = (at == -1 ? fbase : fbase.substring(0, at));
                String mversion = (at == -1 ? "" : fbase.substring(at + 1));
                File mdir = new File(new File(library, mname), mversion);
                if (mdir.exists() && mdir.lastModified() > f.lastModified()) {
                    log("Skipping " + f + " because installed module is up to date",
                        Project.MSG_VERBOSE);
                    continue;
                }
                log("Deleting " + mdir, Project.MSG_VERBOSE);
                delete(mdir);
                files.add(f);
            }
        }

        if (files.isEmpty())
            return;

        try {
            List<String> opts = new ArrayList<String>();
            add(opts, "install");
            add(opts, "--library", library.getPath());
            for (File f: files)
                add(opts, f.getPath());

            log("Installing " + files.size()
                    + " module" + (files.size() == 1 ? "" : "s")
                    + " in " + library,
                    Project.MSG_INFO);
            jmod(opts);
        } catch (InterruptedException e) {
            throw new BuildException("error occurred", e);
        } catch (IOException e) {
            throw new BuildException("error occurred", e);
        }
    }

    void jmod(List<String> opts) throws IOException, InterruptedException {
        if (fork) {
            List<String> cmd = new ArrayList<String>();
            cmd.add(executable);
            cmd.addAll(opts);
//            log("exec: " + cmd, Project.MSG_VERBOSE);
//            ProcessBuilder pb = new ProcessBuilder(cmd);
//            pb.redirectErrorStream(true);
//            Process p = pb.start();
//            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
//            try {
//                String line;
//                while ((line = in.readLine()) != null)
//                    System.out.println(line);
//            } finally {
//                in.close();
//            }
//            int rc = p.waitFor();
//            if (rc != 0)
//                throw new BuildException("unexpected exit from jmod: " + rc);
            Execute.runCommand(this, cmd.toArray(new String[cmd.size()]));
        } else {
            throw new BuildException("fork=false not supported yet");
        }
    }

    boolean delete(File file) {
        if (file.isDirectory()) {
            boolean ok = true;
            for (File f: file.listFiles())
                ok &= delete(f);
            ok &= file.delete();
            return ok;
        } else
            return file.delete();
    }

    void add(List<String> list, String... values) {
        for (String v: values)
            list.add(v);
    }

    private String task;
    private File library;
    private File parent;
    private boolean fork;
    private String executable;
    private List<Commandline.Argument> args = new ArrayList<Commandline.Argument>();
}
