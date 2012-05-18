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

#include <windows.h>
#include "jdk_util.h"

#define JVM_DLL "jvm.dll"

static HMODULE jvm_handle = NULL;

int JDK_InitJvmHandle() {
    jvm_handle = GetModuleHandle(JVM_DLL);
    return (jvm_handle != NULL);
}

void* JDK_FindJvmEntry(const char* name) {
    return (void*) GetProcAddress(jvm_handle, name);
}

void* JDK_GetLibraryHandle(const char* name) {
    char dllname[1024];
    jio_snprintf(dllname, sizeof(dllname), "%s%s%s", JNI_LIB_PREFIX, name, JNI_LIB_SUFFIX);
    return GetModuleHandle(dllname);
}

void* JDK_LookupSymbol(void* handle, const char* name) {
    return(void*) GetProcAddress((HMODULE)handle, name);
}

JNIEXPORT HMODULE JDK_LoadSystemLibrary(const char* name) {
    HMODULE handle = NULL;
    char path[MAX_PATH];

    if (GetSystemDirectory(path, sizeof(path)) != 0) {
        strcat(path, "\\");
        strcat(path, name);
        handle = LoadLibrary(path);
    }

    if (handle == NULL) {
        if (GetWindowsDirectory(path, sizeof(path)) != 0) {
            strcat(path, "\\");
            strcat(path, name);
            handle = LoadLibrary(path);
        }
    }
    return handle;
}

