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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.util.FileUtils;

/**
 * This class provides a specialized form of jpkg, to create a module package file
 * from the appropriate langtools classes, and (re)using the module-info.class
 * file from a previously installed copy of the module.
 * If the package file exists and is up to date, it will not be modified.
 *
 * Note: the code here relies on the internal layout of a module library.
 * Ideally, there would be tool support to extract module info.class from
 * a library.
 */
public class FpkgTask extends MatchingTask {
    /**
     * Set the module name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the module version.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Set the directory in which to write the package file.
     * See jpkg --dest-dir.
     */
    public void setDestDir(File destDir) {
        this.destDir = destDir;
    }

    /**
     * Set the type of package to be written: (deb or jmod)
     * See the first anonymous arg for jpkg.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Set the root directory of the implicit fileset of files to be included
     * in the package.
     * All the related attributes of an implicit fileset may be used.
     * The fileset may include classes (*.class) and resources (other files.)
     */
    public void setDir(File dir) {
        this.dir = dir;
    }

    /**
     * Set the JDK from which to import the copy of module-info to be used.
     * It is assumed the module library will be at jdk/lib/modules
     * and that the library follows the SimpleLibrary layout.
     */
    public void setJDK(File jdk) {
        this.jdk = jdk;
    }

    /**
     * Set whether or not jpkg should be executed in a separate process.
     */
    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Set the path for the executable for jpkg, for use when fork is true.
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    @Override
    public void execute() {
        if (name == null || name.length() == 0)
            throw new BuildException("no name given");

        if (version == null || version.length() == 0)
            throw new BuildException("no version given");

        if (destDir == null)
            throw new BuildException("no output directory given");

        if (type == null)
            type = "jmod";
        else if (!(type.equals("jmod") || type.equals("deb")))
            throw new BuildException("invalid type given");

        // if fork is true, validate executable?

        File file = new File(destDir, name + "@" + version + "." + type);
        if (isUptodate(file)) {
            log("Skipping " + name + "@" + version + " because it is up to date",
                    Project.MSG_VERBOSE);
            return;
        }

        // The following code presumes knowledge of the layout of the JDK module library
        File jdkModulesDir = new File(new File(jdk, "lib"), "modules");
        File jdkModulesMarker = new File(jdkModulesDir, "%jigsaw-library");
        if (!jdkModulesMarker.exists())
            throw new BuildException("cannot find JDK module library");

        File importModuleDir = new File(new File(jdkModulesDir, name), version);
        if (!importModuleDir.exists())
            throw new BuildException("cannot find module " + name + "@" + version + " in JDK module library");

        File importModuleInfo = new File(importModuleDir, "info");
        if (!importModuleInfo.exists())
            throw new BuildException("cannot find module info for module " + name + "@" + version + " in JDK module library");

        File javaTmpDir = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = new File(javaTmpDir, (getClass().getSimpleName() + "." + System.currentTimeMillis()));

        try {
            File tmpModuleDir = new File(tmpDir, name);
            File tmpClassesDir = new File(tmpModuleDir, "classes");

            tmpClassesDir.mkdirs();
            destDir.mkdirs();
            FileUtils fu = FileUtils.getFileUtils();
            fu.copyFile(importModuleInfo, new File(tmpClassesDir, "module-info.class"));
            copyFiles(tmpClassesDir);

            List<String> opts = new ArrayList<String>();
            add(opts, "--module-dir", tmpClassesDir.getPath());
            add(opts, "--dest-dir", destDir.getPath());
            add(opts, type);
            add(opts, name);

            log("Creating module file for " + name, Project.MSG_INFO);
            jpkg(opts);
        } catch (InterruptedException e) {
            throw new BuildException("error occurred", e);
        } catch (IOException e) {
            throw new BuildException("error occurred", e);
        } finally {
            delete(tmpDir);
        }
    }

    void jpkg(List<String> opts) throws IOException, InterruptedException {
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
//                throw new BuildException("unexpected exit from jpkg: " + rc);
            Execute.runCommand(this, cmd.toArray(new String[cmd.size()]));
        } else {
            throw new BuildException("fork=false not supported yet");
        }
    }

    boolean isUptodate(File file) {
        long fileTime = file.lastModified();

        DirectoryScanner s = getDirectoryScanner(dir);
        for (String path: s.getIncludedFiles()) {
            File f = new File(dir, path);
            if (f.lastModified() > fileTime)
                return false;
        }

        return true;
    }

    void copyFiles(File classesDir) throws IOException {
        FileUtils fu = FileUtils.getFileUtils();
        DirectoryScanner s = getDirectoryScanner(dir);
        for (String path: s.getIncludedFiles()) {
            File toDir = classesDir;
            fu.copyFile(new File(dir, path), new File(toDir, path));
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

    private String name;
    private String version;
    private File destDir;
    private String type;
    private File dir;
    private File jdk;
    private boolean fork;
    private String executable;

}
