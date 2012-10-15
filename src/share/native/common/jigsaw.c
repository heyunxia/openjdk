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
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

#ifdef _MSC_VER
#include "dirent_md.h"
#else
#include <unistd.h>
#endif

#include "jdk_util.h"
#include "jigsaw.h"

/*
 * This native interface provides an API for the JVM to find and read
 * classbytes of a given class name from the module library and/or
 * a modulepath.  It needs to find the module matching a given module query
 * that can specify a view name or alias and also the ability to do
 * version comparison to determine the most recent version if multiple
 * modules match the query.
 *
 * In other words, the following have to be done in native:
 * 1. JigsawVersion and JigsawVersionQuery - NOT implemented in native yet
 * 2. Library.findLatestModuleId(ModuleIdQuery)
 * 3. Library.readConfiguration(ModuleId)
 * 4. Configuration.getContextForModuleName
 * 5. Library.readLocalClass
 * 6. modulepath support - NOT implemented yet
 *
 * There would be quite some amount of code that are implemented
 * in both Java and native.  Revisit: this is not ideal and causes
 * maintainence overhead in keeping the native and Java version
 * in sync.
 */

#define MAGIC                   0xcafe00fa
#define MAJOR_VERSION           0
#define MINOR_VERSION           1
#define DEFLATED                (1 << 0)
#define LIBRARY_HEADER          0
#define LIBRARY_MODULE_INDEX    1
#define LIBRARY_MODULE_CONFIG   2
#define LIBRARY_MODULE_IDS      8
#define MODULE_INFO_MAGIC       0xcafebabe

#define JDK_BASE                "jdk.base"
#define JDK_CLASSPATH           "jdk.classpath"
#define JIGSAW_LIBRARY          "%jigsaw-library"
#define JIGSAW_MIDS             "%mids"
#define CONFIG                  "config"
#define MODULE_INFO             "info"
#define CLASSES                 "classes"

#ifdef _MSC_VER
#define FILE_SEPARATOR          '\\'
#else
#define FILE_SEPARATOR          '/'
#endif

/* Type definitions for zip file and zip file entry */
typedef void* jzfile;
typedef struct {
    char*  name;                   /* entry name */
    jlong  time;                   /* modification time */
    jlong  size;                   /* size of uncompressed data */
    jlong  csize;                  /* size of compressed data (zero if uncompressed) */
    jint   crc;                    /* crc of uncompressed data */
    char*  comment;                /* optional zip file comment */
    jbyte* extra;                 /* optional extra data */
    jlong  pos;                    /* position of LOC header (if negative) or data */
} jzentry;

struct library {
    struct library*  parent;
    const char*      path;
};

typedef struct {
    struct jcontext* context;
    const char*      module_name;
    const char*      module_version;
    const char*      libpath;
    const char*      source;
    jzfile*          zfile;
    jzentry*         last_read_entry;
} jmoduleEntry;

typedef struct jcontext {
    jboolean         bootstrap;
    char*            name;
    jint             mcount;
    jmoduleEntry*    modules;
} jcontext;

typedef struct jconfig {
    jboolean         classpath_mode;
    struct library*  mlib;
    const char*      path;
    const char*      config;
    jint             cxcount;
    jcontext*        contexts;
    jcontext*        base;
    jmoduleEntry*    base_module;
} jconfig;

struct ModuleIdEntry {
    const char* mid;
    const char* providingModuleId;
};

static const char separator[2] = { FILE_SEPARATOR, '\0' };

static jconfig* config  = NULL;
static jboolean debugOn = JNI_FALSE;

/* Entry points in zip.dll for loading zip/jar file entries */
/* ## should we use ReadMappedEntry? */
typedef void **  (JNICALL *ZipOpen_t)(const char *name, char **pmsg);
typedef void     (JNICALL *ZipClose_t)(jzfile *zip);
typedef jzentry* (JNICALL *FindEntry_t)(jzfile *zip, const char *name, jint *sizeP, jint *nameLen);
typedef jboolean (JNICALL *ReadEntry_t)(jzfile *zip, jzentry *entry, unsigned char *buf, char *namebuf);
typedef jzentry* (JNICALL *GetNextEntry_t)(jzfile *zip, jint n);

static ZipOpen_t           ZipOpen            = NULL;
static ZipClose_t          ZipClose           = NULL;
static FindEntry_t         FindEntry          = NULL;
static ReadEntry_t         ReadEntry          = NULL;
static GetNextEntry_t      GetNextEntry       = NULL;

#define CLOSE_FD_RETURN(fd, rc)     { JVM_Close(fd); return rc; }

void trace(const char* format, ...) {
    if (debugOn) {
        va_list ap;
        va_start(ap, format);
        vprintf(format, ap);
        va_end(ap);
    }
}

jint initialize() {
    void* handle;
    char* s;

    if (ZipOpen != NULL) {
      // already initialized
      return 0;
    }

    s = getenv("JIGSAW_NATIVE_DEBUG");
    if (s != NULL && strcmp(s, "true") == 0) {
        debugOn = JNI_TRUE;
    }

    if ((handle = JDK_GetLibraryHandle("zip")) == NULL) {
        trace("error: failed to get the handle of %s\n",
              JNI_LIB_PREFIX "zip" JNI_LIB_SUFFIX);
        return JIGSAW_ERROR_ZIP_LIBRARY_NOT_FOUND;
    }

    ZipOpen   = (ZipOpen_t)   JDK_LookupSymbol(handle, "ZIP_Open");
    ZipClose  = (ZipClose_t)  JDK_LookupSymbol(handle, "ZIP_Close");
    FindEntry = (FindEntry_t) JDK_LookupSymbol(handle, "ZIP_FindEntry");
    ReadEntry = (ReadEntry_t) JDK_LookupSymbol(handle, "ZIP_ReadEntry");
    GetNextEntry = (GetNextEntry_t) JDK_LookupSymbol(handle, "ZIP_GetNextEntry");
    if (ZipOpen == NULL || ZipClose == NULL || FindEntry == NULL ||
           ReadEntry == NULL || GetNextEntry == NULL) {
        return JIGSAW_ERROR_ZIP_LIBRARY_NOT_FOUND;
    }
    return 0;
}

unsigned int convertToInt(char* buf, jint count) {
    unsigned int value = 0;
    int n=0;
    while (n < count) {
        value = (value << 8) + (unsigned char) buf[n++];
    }
    return value;
}

unsigned int readByte(jint fd) {
    char buf[1];
    jint len = JVM_Read(fd, buf, 1);
    assert(len == 1);
    return convertToInt(buf, 1);
}
unsigned int readShort(jint fd) {
    char buf[2];
    jint len = JVM_Read(fd, buf, 2);
    assert(len == 2);
    return convertToInt(buf, 2);
}

unsigned int readInt(jint fd) {
    char buf[4];
    jint len = JVM_Read(fd, buf, 4);
    assert(len == 4);
    return convertToInt(buf, 4);
}

unsigned int readLong(jint fd) {
    char buf[8];
    jint len = JVM_Read(fd, buf, 8);
    assert(len == 8);
    return convertToInt(buf, 4);
}

char* readUTF8(jint fd) {
    jint len;
    jint size = readShort(fd);
    char* buf = malloc(size+1);
    if (buf == NULL)
        return NULL;
    len = JVM_Read(fd, buf, size);
    assert(len == size);
    buf[len] = '\0';
    return buf;
}

void skip(jint fd, jlong offset) {
    jlong rc = JVM_Lseek(fd, offset, SEEK_CUR);
    if (rc < 0) {
        trace("lseek fails to %s\n", offset);
    }
}

jint checkFileHeader(jint fd, jint count, unsigned int expected) {
    unsigned int value;
    switch (count) {
        case 2: value = readShort(fd);
                break;
        case 4: value = readInt(fd);
                break;
        default: // unsupported
                assert(JNI_FALSE);
                return -1;
    }

    if (value != expected) {
        trace("error: checkFileContent %d value %d (0x%x) expected %d (0x%x)\n",
               count, value, value, expected, expected);
        return JIGSAW_ERROR_BAD_FILE_HEADER;
    }
    return 0;
}

jint checkModuleHandle(void *m) {
    jmoduleEntry* module = (jmoduleEntry*) m;
    int i;
    if (module == NULL)
        return -1;

    for (i=0; i < config->cxcount; i++) {
        jcontext* cx = &(config->contexts[i]);
        if (cx == module->context)
            return 0;
    }
    return JIGSAW_ERROR_INVALID_MODULE;
}

/*
 * This is called by Java_org_openjdk_jigsaw_ClassPathContext_initBootstrapContexts
 */
void init_bootstrap_contexts(const char** non_bootstrap_modules, jint len) {
    jcontext* cx;
    jmoduleEntry* module;
    int n, i, j;
    int bcxCount = 0;

    // config may not be loaded if jigsaw native interface is not used
    if (config == NULL) return;

    for (n = 0; n < config->cxcount; n++) {
        jboolean bootstrap = JNI_TRUE;
        cx = &config->contexts[n];
        for (i = 0; i < cx->mcount && bootstrap; i++) {
            module = &cx->modules[i];
            for (j = 0; j < len; j++) {
                if (strcmp(module->module_name, non_bootstrap_modules[j]) == 0) {
                    bootstrap = JNI_FALSE;
                    break;
                }
            }
        }
        cx->bootstrap = bootstrap;
        if (bootstrap)
            bcxCount++;
        trace("%s: %d modules bootstrap %d\n", cx->name, cx->mcount, cx->bootstrap);
    }
    trace("bootstrap_contexts inited %d bootstrap contexts %d non-bootstrap contexts (%d modules)\n",
          bcxCount, (config->cxcount-bcxCount), len);

}

/*
 * Read the configuration.
 *
 * TODO: it currently doesn't store the local class map, remote package map,
 * and the remote contexts. There is a footprint concern if these
 * maps are stored in both native and Java for all contexts.
 *
 * Revisit this with the fast configuration.
 */
jint load_config(jconfig* config) {
    jint fd;
    int nRoots, nContexts, nPkgs;
    int nClasses, nRemotePkgs, nSuppliers;
    int nServices;
    int n;
    trace("load_config %s\n", config->config);
    fd = JVM_Open(config->config, O_RDONLY, 0666);
    if (fd == -1) {
        return JIGSAW_ERROR_OPEN_CONFIG;
    }

    if (checkFileHeader(fd, 4, MAGIC) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_BAD_CONFIG);

    if (checkFileHeader(fd, 2, LIBRARY_MODULE_CONFIG) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_BAD_CONFIG);

    if (checkFileHeader(fd, 2, MAJOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_BAD_CONFIG);

    if (checkFileHeader(fd, 2, MINOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_BAD_CONFIG);

    // # roots
    // array of module ids
    // # contexts
    // array of contexts, each:
    //    context's name
    //    # modules
    //    array of modules, each:
    //        module id
    //        library path of the module  or ""
    //        # module views
    //        array of module ids for the views
    // size of local class map
    // local class to module id entries
    // size of remote package map
    // remote package to context name's entries
    // size of suppliers
    // array of remote context's name

    nRoots = readInt(fd);
    for (n=0; n < nRoots; n++) {
        char* s = readUTF8(fd);
        trace("root[%d] = %s\n", n, s);
        free(s);
    }
    nContexts = readInt(fd);
    config->cxcount = nContexts;
    config->contexts = malloc(sizeof(jcontext) * nContexts);
    for (n=0; n < nContexts; n++) {
        jcontext* cx = &config->contexts[n];
        cx->name = readUTF8(fd);
    }

    // packages
    nPkgs = readInt(fd);
    for (n = 0; n < nPkgs; n++) {
        char* pn = readUTF8(fd);
        free(pn);
    }

    for (n=0; n < nContexts; n++) {
        int i, j;
        jcontext* cx = &config->contexts[n];
        int nModules = readInt(fd);
        cx->mcount = nModules;
        cx->modules = malloc(sizeof(jmoduleEntry) * nModules);

        trace("contexts[%d] = %s (%d modules)\n", n, cx->name, nModules);
        for (i=0; i < nModules; i++) {
            jmoduleEntry* m = &cx->modules[i];
            char* mid = readUTF8(fd);
            char* libpath = readUTF8(fd);
            int views = readInt(fd);
            m->context = cx;
            m->libpath = libpath;
            m->module_name = strtok(mid, "@");
            m->module_version = strtok(NULL, "@");
            m->zfile = NULL;
            trace("  modules[%d] = %s @ %s path %s (%d views)\n", i,
                  m->module_name, m->module_version, libpath, views);
            for (j=0; j < views; j++) {
                char* viewname = readUTF8(fd);
                free(viewname);
            }
        }

        // lazy initialized when the ClassPathContexts are created
        cx->bootstrap = JNI_FALSE;

        // local class map
        nClasses = readInt(fd);
        for (i=0; i < nClasses; i++) {
            int pidx = readInt(fd);
            char* name = readUTF8(fd);
            int midx = readInt(fd);
            free(name);
        }

        // remote package map
        nRemotePkgs = readInt(fd);
        for(i=0; i < nRemotePkgs; i++) {
            readInt(fd); // remote package
            readInt(fd); // remote context
        }

        // remote contexts/suppliers
        nSuppliers = readInt(fd);
        for (i=0; i < nSuppliers; i++) {
            readInt(fd); // remote context
        }

        // Local service implementations
        nServices = readInt(fd);
        for (i=0; i < nServices; i++) {
            char* sn = readUTF8(fd);
            int nImpl = readInt(fd);
            for (j=0; j < nImpl; j++) {
                char* cn = readUTF8(fd);
                free(cn);
            }
            free(sn);
        }
    }
    CLOSE_FD_RETURN(fd, 0);
}

// ## TODO: implement jigsaw version comparsion
int version_compare(char* v1, char* v2) {
    return -1;
}

/* Parse a given ModuleId
 */
void parse_module_id(const char* mid, char *name, char* version) {
    int i=0, n=0;
    char c = mid[n++];
    while (c != '\0' && c != '@') {
        name[i++] = c;
        c = mid[n++];
    }
    name[i] = '\0';
    // if mid is an alias, it does not have version
    c = c == '@' ? mid[n++] : c;
    i = 0;
    while (c != '\0') {
        version[i++] = c;
        c = mid[n++];
    }
    version[i] = '\0';
}

/* Finds the module directory storing the content of a module that
 * matches a given ModuleIdQuery. It returns zero if a module is found;
 * it returns -1 if no module matching the query or non-zero error code.
 */
jint find_declaring_module_dir(const char* libpath, const char* modulepath,
                               const char* midq,
                               char* module_dir, int len)
{
    char path[JVM_MAXPATHLEN];
    char name[1024];
    jint fd;
    jint nEntries, n;
    char c;
    char module_name[1024];
    char module_version[1024];
    struct ModuleIdEntry* dictionary;
    struct ModuleIdEntry* entry = NULL;

    trace("find_declaring_module_dir matching %s\n", midq);

    // ## TODO: modulepath support
    if (libpath == NULL) {
        return JIGSAW_ERROR_INVALID_MODULE_LIBRARY;
    }

    jio_snprintf(path, sizeof(path), "%s%s%s", libpath, separator, JIGSAW_MIDS);
    fd = JVM_Open(path, O_RDONLY, 0666);
    if (fd == -1) {
        return JIGSAW_ERROR_INVALID_MODULE_IDS;
    }

    // validate the header of the jigsaw library
    if (checkFileHeader(fd, 4, MAGIC) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_IDS);

    if (checkFileHeader(fd, 2, LIBRARY_MODULE_IDS) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_IDS);

    if (checkFileHeader(fd, 2, MAJOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_IDS);

    if (checkFileHeader(fd, 2, MINOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_IDS);

    nEntries = readInt(fd);
    dictionary = malloc(sizeof(struct ModuleIdEntry) * nEntries);
    for (n=0; n < nEntries; n++) {
        // ## TODO: build a Hashmap for faster lookup
        struct ModuleIdEntry* entry = &(dictionary[n]);
        entry->mid = readUTF8(fd);
        entry->providingModuleId = readUTF8(fd);
        trace("[%d] %s -> %s\n", n, entry->mid, entry->providingModuleId);
    }

    // get ModuleQueryId.name()
    c = *midq;
    n = 0;
    while (c != '\0' && c != '@') {
        name[n++] = c;
        c = midq[n];
    }
    name[n] = '\0';
    module_version[0] = '\0';
    for (n=0; n < nEntries; n++) {
        struct ModuleIdEntry* e = &(dictionary[n]);
        char mn[1024];
        char version[1024];
        parse_module_id(e->mid, mn, version);
        if (strcmp(mn, name) == 0) {
            // module name matches the query
            // use the most recent version, if multiple versions installed
            if (version_compare(module_version, version) < 0) {
                strncpy(module_version, version, sizeof(module_version));
                entry = e;
            }
        }
    }
    if (entry == NULL) {
        trace("no module matches %s\n", midq);
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_MODULE_NOT_FOUND);
    }

    while (strcmp(entry->mid, entry->providingModuleId) != 0) {
        for (n=0; n < nEntries; n++) {
            struct ModuleIdEntry* e = &(dictionary[n]);
            if (strcmp(e->mid, entry->providingModuleId) == 0) {
                entry = e;
                break;
            }
        }
    }
    parse_module_id(entry->mid, module_name, module_version);
    jio_snprintf(module_dir, len, "%s%s%s%s%s",
                 libpath, separator, module_name, separator, module_version);
    CLOSE_FD_RETURN(fd, 0);
}


/* Finds the configuration of a module that matches the given ModuleIdQuery.
 * It returns a newly allocated config struct;
 * otherwise, returns NULL.
 */
jconfig* find_config(struct library* mlib,
                     const char* modulepath,
                     const char* midq)
{
    char mdir[JVM_MAXPATHLEN];
    char config_path[JVM_MAXPATHLEN];
    const char* path;
    jconfig* config;

    struct library* lib = mlib;
    while (lib != NULL) {
        int rc = find_declaring_module_dir(lib->path, modulepath, midq, mdir, sizeof(mdir));
        if (rc == 0) {
            jio_snprintf(config_path, sizeof (config_path), "%s%s%s",
                         mdir, separator, CONFIG);

            config = malloc(sizeof (jconfig));
            config->config = strdup(config_path);
            config->path = strdup(lib->path);
            return config;
        }
        lib = lib->parent;
    }
    return NULL;
}

// Find the ZipEntry for the given class in a simple library
jint find_class_entry(jmoduleEntry* m, const char* name, jzentry** entry, jint* filesize, jint* name_len) {
    if (m->zfile == NULL) {
        // cache the opened zip file for the module
        char path[JVM_MAXPATHLEN];
        char* error_msg = NULL;
        const char* libpath = m->libpath;
        if (m->libpath[0] == '\0') {
            libpath = config->path;
        }
        jio_snprintf(path, sizeof(path), "%s%s%s%s%s%s%s", libpath,
                     separator, m->module_name, separator, m->module_version, separator, CLASSES);
        m->source = strdup(path);
        trace("open zip file %s\n", m->source);
        m->zfile = (*ZipOpen)(m->source, &error_msg);
        if (m->zfile == NULL) {
            trace("%s: %s\n", error_msg, path);
            return JIGSAW_ERROR_CLASS_NOT_FOUND;
        }
    }

    *entry = (*FindEntry)(m->zfile, name, filesize, name_len);
    if (*entry == NULL) {
        return JIGSAW_ERROR_CLASS_NOT_FOUND;
    }
    return 0;
}

jmoduleEntry* find_class(jcontext* cx, const char* entry_name, jint* filesize) {
    jzentry* entry;
    jmoduleEntry* m;
    int n, name_len;

    // TODO: fast configuration
    // For now, we just iterate through the modules in a context
    // to find the class.
    //
    if (cx == config->base) {
        // Always look at the base module first if it's in the context
        // since it has the classes the VM will mostly find.
        m = config->base_module;
        n = -1;
    } else {
        m = &cx->modules[0];
        n = 0;
    }
    entry = NULL;
    while (entry == NULL && n < cx->mcount) {
        int rc;
        if (strcmp(m->module_name, JDK_CLASSPATH) == 0) {
            // skip the classpath module
            m = &cx->modules[++n];
            continue;
        }
        rc = find_class_entry(m, entry_name, &entry, filesize, &name_len);
        if (rc == 0) {
            break;
        } else if (rc == JIGSAW_ERROR_CLASS_NOT_FOUND && (n+1) < cx->mcount) {
            m = &cx->modules[++n];
            if (m == config->base_module) {
                // skip the base module (already visited)
                m = &cx->modules[++n];
            }
        } else {
            return NULL;
        }
    }
    return entry == NULL ? NULL : m;
}

void free_module_library(struct library *mlib) {
    struct library* p = mlib;
    while (p != NULL) {
        mlib = mlib->parent;
        free(p);
        p = mlib;
    }
}

jint open_module_library(const char* libpath, struct library** mlib) {
    char path[JVM_MAXPATHLEN];
    jint fd;
    jint rc, value;
    char c;
    char* p;
    struct library* parent = NULL;
    char* parentpath = NULL;

    trace("open_module_library %s\n", libpath);
    jio_snprintf(path, sizeof(path), "%s%s%s",
                 libpath, separator, JIGSAW_LIBRARY);

    fd = JVM_Open(path, O_RDONLY, 0666);
    if (fd == -1) {
        trace("error: failed to open %s\n", path);
        return JIGSAW_ERROR_MODULE_LIBRARY_NOT_FOUND;
    }

    if (checkFileHeader(fd, 4, MAGIC) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_LIBRARY);

    if (checkFileHeader(fd, 2, LIBRARY_HEADER) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_LIBRARY);

    if (checkFileHeader(fd, 2, MAJOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_LIBRARY);

    if (checkFileHeader(fd, 2, MINOR_VERSION) != 0)
        CLOSE_FD_RETURN(fd, JIGSAW_ERROR_INVALID_MODULE_LIBRARY);

    value = readShort(fd);  // deflated?
    if (readByte(fd) == 1) {
        parentpath = readUTF8(fd);
        trace("   parent %s\n", parentpath);

        p = parentpath;
        while ((c = *p) != '\0') {
            if (c == '/') {
                *p = FILE_SEPARATOR;
            }
            p++;
        }
        trace("   parent %s\n", parentpath);
    }
    JVM_Close(fd);

    if (parentpath != NULL) {
        parent = (struct library*) malloc(sizeof(struct library));
        rc = open_module_library(parentpath, &parent);
        if (rc != 0) {
            free_module_library(parent);
            return rc;
        }
    }
    *mlib = (struct library*) malloc(sizeof(struct library));
    (*mlib)->parent = parent;
    (*mlib)->path = libpath;
    return 0;
}

/*
 * Load the contexts of a given module query and set the
 * context argument to the handle for subsequent class lookup.
 * This method returns 0 if succeed; otherwise returns non-zero
 * error code.
 *
 * libpath      : module library path (must be non-NULL)
 * modulepath   : module path or NULL
 * module_query : module query in module mode or NULL in classpath mode
 * context      : To be set with the handle to the context
 *                for class lookup
 */
jint
JDK_LoadContexts(const char *libpath, const char *modulepath,
                 const char *module_query,
                 void **context)
{
    char path[JVM_MAXPATHLEN];
    int n, rc;
    char* config_path;
    struct library* mlib;

    if (initialize() != 0)
        return -1;

    trace("JDK_LoadContexts %s %s\n", libpath, module_query);

    // ## TODO: modulepath support
    if (libpath == NULL) {
        return JIGSAW_ERROR_INVALID_MODULE_LIBRARY;
    }

    rc = open_module_library(libpath, &mlib);
    if (rc != 0) {
        free_module_library(mlib);
        return rc;
    }

    if (module_query != NULL) {
        config = find_config(mlib, modulepath, module_query);
    } else {
        config = find_config(mlib, modulepath, JDK_CLASSPATH);
        if (config == NULL) {
            trace("classpath config for %s not found\n", JDK_CLASSPATH);
            config = find_config(mlib, modulepath, JDK_BASE);
        }
    }
    if (config == NULL) {
        trace("error: config not found\n");
        return JIGSAW_ERROR_MODULE_NOT_FOUND;
    }

    config->classpath_mode = (module_query == NULL);
    if ((rc = load_config(config)) != 0) {
        trace("error: failed to load config %s\n", config->config);
        return rc;
    }
    config->base = NULL;
    config->base_module = NULL;
    n=0;
    while (n < config->cxcount && config->base == NULL) {
        int i;
        jcontext* cx = &config->contexts[n++];
        trace("%s: %d modules bootstrap %d\n", cx->name, cx->mcount, cx->bootstrap);
        for (i=0; i < cx->mcount; i++) {
            if (strcmp(cx->modules[i].module_name, JDK_BASE) == 0) {
                 config->base = cx;
                 config->base_module = &cx->modules[i];
                 break;
            }
        }
    }

    if (config->base == NULL)
        return JIGSAW_ERROR_BASE_MODULE_NOT_FOUND;

    *context = config->base;
    return 0;
}

/*
 * Finds the class of a given classname local in a given context
 * This method returns 0 if the class is found; otherwise returns
 * non-zero error code.
 *
 * context    : handle to the context
 * classname  : fully-qualified class name (in UTF8 format)
 * module     : handle to the module containing the class
 * len        : length of the class data
 */
jint
JDK_FindLocalClass(void *context,
                   const char *classname,
                   void **module,
                   jint *len)
{
    char entry_name[JVM_MAXPATHLEN];
    jmoduleEntry* m;
    int n;

    jcontext* cx = (jcontext*) context;
    if (config == NULL) {
        return JIGSAW_ERROR_CONTEXTS_NOT_LOADED;
    }
    // Temporary for testing purpose
    if (context == NULL)
        cx = config->base;

    if (cx != NULL && cx != config->base) {
        return JIGSAW_ERROR_INVALID_CONTEXT;
    }

    assert (strlen(classname) < JVM_MAXPATHLEN-6);
    strncpy(entry_name, classname, sizeof(entry_name));
    strcat(entry_name, ".class");

    // find class from the base context
    m = find_class(cx, entry_name, len);
    if (m == NULL && config->classpath_mode) {
        // classpath mode: search all contexts
        // assert bootstrap initialized
        for (n=0; n < config->cxcount; n++) {
            jcontext* cx = &config->contexts[n];
            // base has been visited; skip non-bootclasspath modules
            if (cx == config->base || !cx->bootstrap)
                continue;
            if ((m = find_class(cx, entry_name, len)) != NULL)
                break;
        }
    }
    if (m == NULL) {
        return JIGSAW_ERROR_CLASS_NOT_FOUND;
    }

    *module = (void*) m;
    return 0;
}

/*
 * Reads bytestream of a given classname local in a given context
 * This method returns 0 if succeed; otherwise returns non-zero
 * error code.
 *
 * module     : handle to the module containing the class
 * classname  : fully-qualified class name (in UTF8 format)
 * buf        : an allocated buffer to store the class data
 * len        : length of the buffer
 */
jint
JDK_ReadLocalClass(void *module,
                   const char *classname,
                   unsigned char *buf,
                   jint len)
{
    jint filesize, name_len;
    jzentry* entry;
    jmoduleEntry* m;
    char entry_name[JVM_MAXPATHLEN];
    char name_buf[128];
    char* filename;
    int rc;

    if (config == NULL) {
        return JIGSAW_ERROR_CONTEXTS_NOT_LOADED;
    }
    if (checkModuleHandle(module)) {
        return JIGSAW_ERROR_INVALID_MODULE;
    }

    assert (strlen(classname) < JVM_MAXPATHLEN-6);
    strncpy(entry_name, classname, sizeof(entry_name));
    strcat(entry_name, ".class");

    entry = NULL;
    m = (jmoduleEntry*)module;
    rc = find_class_entry(m, entry_name, &entry, &filesize, &name_len);
    if (rc != 0) {
        return rc;
    }
    if (name_len < 128) {
      filename = name_buf;
    } else {
      filename = malloc(name_len + 1);
    }

    if (!(*ReadEntry)(m->zfile, entry, buf, filename)) {
        trace("failed to read entry %s\n", classname);
        return JIGSAW_ERROR_READ_CLASS_ENTRY;
    }

    return 0;
}

/*
 * Get the information about the given module.
 *
 * module    : handle to a module
 * minfo     : a pointer to struct for the module information.
 *
 * TODO: what the source should be? It's used by the VM to print
 * verbose output.  Currently set to the path to
 * <modulename>/<version>/classes.
 *
 * defineClass passes the module's code source to the VM.
 *
 * Might be better to print module library + module ID in verbose output.
 */
jint
JDK_GetModuleInfo(void *module,
                  jmodule *minfo) {
    jmoduleEntry* m = (jmoduleEntry*)module;

    if (config == NULL) {
        return JIGSAW_ERROR_CONTEXTS_NOT_LOADED;
    }
    if (checkModuleHandle(module) != 0) {
        return JIGSAW_ERROR_INVALID_MODULE;
    }

    minfo->libpath = m->libpath;
    minfo->module_name = m->module_name;
    minfo->module_version = m->module_version;
    minfo->source = m->source;
    return 0;
}

/*
 * Set the path of the system module library of the given JAVA_HOME
 * to the given libpath.  This method returns 0 if succeed; otherwise
 * returns non-zero error code.
 *
 * java_home : JAVA_HOME
 * libpath   : allocated buffer to be set with the path
 *             of the system module library
 * len       : length of the libpath argument
 */
jint
JDK_GetSystemModuleLibraryPath(const char* java_home,
                               char* libpath,
                               size_t len)
{
    size_t rv;
    jint err;
    struct library* mlib;

    if (initialize() != 0)
        return -1;

    trace("JDK_GetSystemModuleLibraryPath %s\n", java_home);
    if (java_home == NULL) {
        return JIGSAW_ERROR_MODULE_LIBRARY_NOT_FOUND;
    }

    rv = jio_snprintf(libpath, len, "%s%slib%smodules",
                      java_home, separator, separator);
    if (rv >= len) {
        trace("error: buffer size too small %d len %d\n", rv, len);
        return JIGSAW_ERROR_BUFFER_TOO_SHORT;
    }
    return 0;
}
