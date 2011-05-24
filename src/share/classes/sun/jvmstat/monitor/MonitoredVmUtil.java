/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvmstat.monitor;

/**
 * Utility class proving concenience methods for extracting various
 * information from an MonitoredVm object.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class MonitoredVmUtil {

    /**
     * Private constructor - prevent instantiation.
     */
    private MonitoredVmUtil() { }

    /**
     * Return the Java Virtual Machine Version.
     *
     * @param vm the target MonitoredVm
     * @return String - contains the version of the target JVM or the
     *                  the string "Unknown" if the version cannot be
     *                  determined.
     */
    public static String vmVersion(MonitoredVm vm) throws MonitorException {
        StringMonitor ver =
               (StringMonitor)vm.findByName("java.property.java.vm.version");
        return (ver == null) ? "Unknown" : ver.stringValue();
    }

    /**
     * Return the command line for the target Java application.
     *
     * @param vm the target MonitoredVm
     * @return String - contains the command line of the target Java
     *                  application or the the string "Unknown" if the
     *                  command line cannot be determined.
     */
    public static String commandLine(MonitoredVm vm) throws MonitorException {
        StringMonitor cmd = (StringMonitor)vm.findByName("sun.rt.javaCommand");
        return (cmd == null) ? "Unknown" : cmd.stringValue();
    }

    /**
     * Returns true if the application is a java launcher tool running
     * in module name.
     *
     * The workaround for multi-entry point support launches 
     * jdk tools by sun.launcher.LauncherHelper in module mode
     * and the module name is added in the java command before the main
     * class name of the tool.
     */
    private static boolean isJavaToolModule(MonitoredVm vm)
            throws MonitorException
    {

        // It's a java launcher tool if the sun.rt.javaMain value is 
        // different than the first argument in the java command.
        StringMonitor javaMain = (StringMonitor)vm.findByName("sun.rt.javaMain");
        if (javaMain == null)
            return false;
        
        StringMonitor javaModule = (StringMonitor)vm.findByName("sun.rt.javaModule");
        if (javaModule == null || javaModule.stringValue().isEmpty())
            return false;
 
        String name = javaMain.stringValue();
        String moduleId = javaModule.stringValue();
        int i = moduleId.indexOf('@');   // moduleId may contain @version
        String modulename = (i >= 0) ? moduleId.substring(0, i)
                                     : moduleId;
        return !name.equals(modulename) && !name.equals(moduleId);
    }

    /**
     * Return the arguments to the main class for the target Java application.
     * Returns the arguments to the main class. If the arguments can't be
     * found, the string "Unknown" is returned.
     *
     * @param vm the target MonitoredVm
     * @return String - contains the arguments to the main class for the
     *                  target Java application or the the string "Unknown"
     *                  if the command line cannot be determined.
     */
    public static String mainArgs(MonitoredVm vm) throws MonitorException {
        String commandLine = commandLine(vm);

        int index = commandLine.indexOf(' ');
        if (index > 0 && isJavaToolModule(vm)) {
            // skip the module name and the class name if it's a Java launcher tool
            index = commandLine.indexOf(' ', index+1);
        }

        if (index > 0) {
            return commandLine.substring(index + 1);
        } else if (commandLine.compareTo("Unknown") == 0) {
            return commandLine;
        } else {
            return null;
        }
    }

    /**
     * Return the main class for the target Java application.
     * Returns the main class or the name of the jar file if the application
     * was started with the <em>-jar</em> option.
     *
     * @param vm the target MonitoredVm
     * @param fullPath include the full path to Jar file, where applicable
     * @return String - contains the main class of the target Java
     *                  application or the the string "Unknown" if the
     *                  command line cannot be determined.
     */
    public static String mainClass(MonitoredVm vm, boolean fullPath)
                         throws MonitorException {
        String arg0;
        StringMonitor javaMain = (StringMonitor)vm.findByName("sun.rt.javaMain");
        if (javaMain != null) {
            arg0 = javaMain.stringValue();
        } else {
            String commandLine = commandLine(vm);
            int firstSpace = commandLine.indexOf(' ');
            arg0 = firstSpace > 0
                       ? commandLine.substring(0, firstSpace)
                       : commandLine;
        }

        StringMonitor javaModule = (StringMonitor)vm.findByName("sun.rt.javaModule");
        if (javaModule != null &&
                !javaModule.stringValue().isEmpty() &&
                !isJavaToolModule(vm)) {
            // return the full module name if it's a module
            // except java tools
            return arg0;
        }

        if (!fullPath) {
            /*
             * can't use File.separator() here because the separator
             * for the target jvm may be different than the separator
             * for the monitoring jvm.
             */
            int lastFileSeparator = arg0.lastIndexOf('/');
            if (lastFileSeparator > 0) {
                 return arg0.substring(lastFileSeparator + 1);
            }

            lastFileSeparator = arg0.lastIndexOf('\\');
            if (lastFileSeparator > 0) {
                 return arg0.substring(lastFileSeparator + 1);
            }

            int lastPackageSeparator = arg0.lastIndexOf('.');
            if (lastPackageSeparator > 0) {
                 return arg0.substring(lastPackageSeparator + 1);
            }
        }
        return arg0;
    }

    /**
     * Return the JVM arguments for the target Java application.
     *
     * @param vm the target MonitoredVm
     * @return String - contains the arguments passed to the JVM for the
     *                  target Java application or the the string "Unknown"
     *                  if the command line cannot be determined.
     */
    public static String jvmArgs(MonitoredVm vm) throws MonitorException {
        StringMonitor jvmArgs = (StringMonitor)vm.findByName("java.rt.vmArgs");
        return (jvmArgs == null) ? "Unknown" : jvmArgs.stringValue();
    }

    /**
     * Return the JVM flags for the target Java application.
     *
     * @param vm the target MonitoredVm
     * @return String - contains the flags passed to the JVM for the
     *                  target Java application or the the string "Unknown"
     *                  if the command line cannot be determined.
     */
    public static String jvmFlags(MonitoredVm vm) throws MonitorException {
        StringMonitor jvmFlags =
               (StringMonitor)vm.findByName("java.rt.vmFlags");
        return (jvmFlags == null) ? "Unknown" : jvmFlags.stringValue();
    }

    // Index of the sun.rt.jvmCapabilities counter
    private static int IS_ATTACHABLE = 0;
    private static int IS_KERNEL_VM  = 1;

    /**
     * Returns true if the VM supports attach-on-demand.
     *
     * @param vm the target MonitoredVm
     */
    public static boolean isAttachable(MonitoredVm vm) throws MonitorException {
        StringMonitor jvmCapabilities =
               (StringMonitor)vm.findByName("sun.rt.jvmCapabilities");
        if (jvmCapabilities == null) {
             return false;
        } else {
             return jvmCapabilities.stringValue().charAt(IS_ATTACHABLE) == '1';
        }
    }

}
