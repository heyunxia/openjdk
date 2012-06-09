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

#ifdef _WIN32           /* needed for htonl, Ugh! */
#include <winsock2.h>
#else
#include <netinet/in.h>
#endif

#include "jni.h"
#include "jlong.h"
#include "jni_util.h"
#include "org_openjdk_jigsaw_PersistentTreeMap.h"
#include "PersistentTreeMap.h"

static jboolean
error(JNIEnv *env, int rv)
{
    if (rv != 0) {
        char *m = libdb_db_strerror(rv);
        JNU_ThrowIOException(env, m);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static const char *
getutf(JNIEnv *env, jstring s)
{
    // TODO: replace with GetStringUTFRegion and use on stack buffer
    //       to avoid malloc from GetStringUTFChars???
    const char *u = (*env)->GetStringUTFChars(env, s, NULL);
    if (u == NULL) {
        JNU_ThrowOutOfMemoryError(env, "org.openjdk.jigsaw.PersistentTreeMap.getutf");
        return NULL;
    }
    return u;
}

static void freeutf(JNIEnv *env, jstring s, const char *su)
{
    (*env)->ReleaseStringUTFChars(env, s, su);
}

JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_initialize(JNIEnv *env, jclass cl)
{
    if (libdb_db_create == NULL)
        loadLibrary(env);
}

JNIEXPORT jlong JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_create0(JNIEnv *env, jclass cl,
                                                  jstring path)
{
    DB *dbp;
    int rv;
    const char *pathb = getutf(env, path);
    if (pathb == NULL)
        return 0;

    rv = libdb_db_create(&dbp, NULL, 0);
    if (error(env, rv)) {
        freeutf(env, path, pathb);
        return 0;
    }

    rv = dbp->open(dbp, NULL, pathb, NULL, DB_BTREE,
                   DB_CREATE | DB_TRUNCATE | DB_THREAD, 0);

    freeutf(env, path, pathb);
    if (error(env, rv)) {
        dbp->close(dbp, 0);
        return 0;
    }
    return ptr_to_jlong(dbp);
}

JNIEXPORT jlong JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_open0(JNIEnv *env, jclass cl,
                                                jstring path)
{
    DB *dbp;
    int rv;
    const char *pathb = getutf(env, path);
    if (pathb == NULL)
        return 0;

    rv = libdb_db_create(&dbp, NULL, 0);
    if (error(env, rv)) {
        freeutf(env, path, pathb);
        return 0;
    }

    rv = dbp->open(dbp, NULL, pathb, NULL, DB_BTREE, DB_RDONLY | DB_THREAD, 0);
    freeutf(env, path, pathb);
    if (error(env, rv)) {
        dbp->close(dbp, 0);
        return 0;
    }
    return ptr_to_jlong(dbp);
}

JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_put0(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key,
                                               jstring val)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv;
    const char *v;
    const char *k = getutf(env, key);
    if (k == NULL)
        return;
    v = getutf(env, val);
    if (v == NULL) {
        freeutf(env, key, k);
        return;
    }

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = (void*)v;
    dbdata.size = dbdata.ulen = strlen(v);

    rv = dbp->put(dbp, NULL, &dbkey, &dbdata, 0);

    freeutf(env, key, k);
    freeutf(env, val, v);
    (void)error(env, rv);
}

JNIEXPORT jstring JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_get0(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv;
    char valbuf[1024];     // TODO: 1k limit on data value, increase/malloc?
    const char *k = getutf(env, key);
    if (k == NULL)
        return NULL;

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = valbuf;
    dbdata.ulen = 1023;     // leave space for null terminator
    dbdata.flags = DB_DBT_USERMEM;

    rv = dbp->get(dbp, NULL, &dbkey, &dbdata, 0);
    freeutf(env, key, k);
    if (rv == DB_NOTFOUND || error(env, rv) || dbdata.data == NULL)
        return NULL;

    // TODO: check for DB_BUFFER_SMALL (possibly in error) and handle
    valbuf[dbdata.size] = 0; // null terminate

    return (*env)->NewStringUTF(env, valbuf);
}

JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_put1(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key,
                                               jint val)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv;
    int aval = htonl(val);
    const char *k = getutf(env, key);
    if (k == NULL)
        return;

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = &aval;
    dbdata.size = dbdata.ulen = sizeof(aval);

    rv = dbp->put(dbp, NULL, &dbkey, &dbdata, 0);
    freeutf(env, key, k);
    (void)error(env, rv);
}

JNIEXPORT jint JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_get1(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv;
    jint ival;
    const char *k = getutf(env, key);
    if (k == NULL)
        return -1;

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = &ival;
    dbdata.ulen = sizeof(ival);
    dbdata.flags = DB_DBT_USERMEM;

    rv = dbp->get(dbp, NULL, &dbkey, &dbdata, 0);
    freeutf(env, key, k);
    if (rv == DB_NOTFOUND || error(env, rv) || dbdata.data == NULL)
        return -1;

    return ntohl(ival);
}

JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_put2(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key,
                                               jstring sval, jint ival)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv, slen, sulen, blen;
    char *valbuf;
    int aval = htonl(ival);
    const char *k = getutf(env, key);
    if (k == NULL)
        return;

    slen = (*env)->GetStringLength(env, sval);
    sulen = (*env)->GetStringUTFLength(env, sval);  //TODO; error checking jni fails?
    blen = sulen + sizeof(aval);
    valbuf = (char*)malloc(blen + 1);    //TODO: on stack buffer if small enough?
    memcpy(valbuf, &aval, sizeof(aval));
    (*env)->GetStringUTFRegion(env, sval, 0, slen, valbuf + sizeof(aval));

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = (void*)valbuf;
    dbdata.size = dbdata.ulen = blen;
    dbdata.flags = DB_DBT_USERMEM;

    rv = dbp->put(dbp, NULL, &dbkey, &dbdata, 0);
    freeutf(env, key, k);
    free(valbuf);
    (void)error(env, rv);
}

JNIEXPORT jboolean JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_get2(JNIEnv *env, jobject ob,
                                               jlong dbl, jstring key,
                                               jobjectArray svala,
                                               jintArray ivala)
{
    DB *dbp = jlong_to_ptr(dbl);
    DBT dbkey, dbdata;
    int rv;
    char valbuf[1024];
    jint ival;
    jstring sval;
    const char *k = getutf(env, key);
    if (k == NULL)
        return JNI_FALSE;

    memset(&dbkey, 0, sizeof(DBT));
    memset(&dbdata, 0, sizeof(DBT));
    dbkey.data = (void*)k;
    dbkey.size = dbkey.ulen = strlen(k);
    dbdata.data = valbuf;
    dbdata.ulen = 1023;     // leave space for null terminator
    dbdata.flags = DB_DBT_USERMEM;

    rv = dbp->get(dbp, NULL, &dbkey, &dbdata, 0);
    freeutf(env, key, k);
    if (rv == DB_NOTFOUND || error(env, rv) ||
        dbdata.data == NULL || dbdata.size < sizeof(jint))
        return JNI_FALSE;

    // TODO: check for DB_BUFFER_SMALL (possibly in error) and handle
    valbuf[dbdata.size] = 0; // null terminate

    ival = ntohl(*((jint *)valbuf));
    sval = (*env)->NewStringUTF(env, valbuf + sizeof(ival));
    (*env)->SetObjectArrayElement(env, svala, 0, sval);
    (*env)->SetIntArrayRegion(env, ivala, 0, 1, &ival);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_openjdk_jigsaw_PersistentTreeMap_close0(JNIEnv *env, jobject ob,
                                                 jlong dbl)
{
    DB *dbp = jlong_to_ptr(dbl);
    (void)error(env, dbp->close(dbp, 0));
}
