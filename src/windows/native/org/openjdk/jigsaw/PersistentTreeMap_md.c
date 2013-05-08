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

#include <windows.h>
#include "jni_util.h"
#include "PersistentTreeMap.h"

#ifndef BDB_LIB_NAME
#define BDB_LIB_NAME "db-rds.dll";
#endif

void ThrowException(JNIEnv *env)
{
    char szMessage[1024];
    szMessage[0] = '\0';
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM, NULL, GetLastError(),
                  MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                  szMessage, 1024, NULL);
    JNU_ThrowInternalError(env, szMessage);
}


// Loads the appripriate BDB library. There will be a
// pending exception if this method fails.
void loadLibrary(JNIEnv* env)
{
    HMODULE handle = NULL;
    const char* libdb = BDB_LIB_NAME;

    if ((handle = LoadLibrary(libdb)) == NULL) {
        ThrowException(env);
        return;
    }

    if ((libdb_db_create = (db_create_func*)
            GetProcAddress(handle, "db_create")) == NULL) {
        ThrowException(env);
        return;
    }

    if ((libdb_db_strerror = (db_strerror_func*)
            GetProcAddress(handle, "db_strerror")) == NULL) {
        ThrowException(env);
    }
}

