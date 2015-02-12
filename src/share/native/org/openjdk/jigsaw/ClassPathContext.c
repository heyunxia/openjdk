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

#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "org_openjdk_jigsaw_ClassPathContext.h"

#include "jigsaw.h"

/*
 * Class:     org_openjdk_jigsaw_ClassPathContext
 * Method:    initBootstrapContexts
 * Signature: ([Ljava/lang/String;I[Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_ClassPathContext_initBootstrapContexts(JNIEnv *env,
                                                               jclass dummy,
                                                               jobjectArray extModules,
                                                               jint extCount,
                                                               jobjectArray cpathModules,
                                                               jint cpathCount)
{
    const char** modules = NULL;
    int len = extCount + cpathCount;
    jsize i, j;
    jstring s;

    if (len > 0) {
        modules = (const char**) malloc(len * sizeof(char*));
        for (i=0, j=0; i < extCount; i++, j++) {
            s = (*env)->GetObjectArrayElement(env, extModules, i);
            modules[j] = JNU_GetStringPlatformChars(env, s, 0);
        }
        for (i=0; i < cpathCount; i++, j++) {
            s = (*env)->GetObjectArrayElement(env, cpathModules, i);
            modules[j] = JNU_GetStringPlatformChars(env, s, 0);
        }
    }
    init_bootstrap_contexts(modules, len);
    for (i=0, j=0; i < extCount; i++, j++) {
        s = (*env)->GetObjectArrayElement(env, extModules, i);
        JNU_ReleaseStringPlatformChars(env, s, modules[j]);
    }
    for (i=0; i < cpathCount; i++, j++) {
        s = (*env)->GetObjectArrayElement(env, cpathModules, i);
        JNU_ReleaseStringPlatformChars(env, s, modules[j]);
    }
}

