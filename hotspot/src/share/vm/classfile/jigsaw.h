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

#ifndef JIGSAW_H
#define JIGSAW_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Jigsaw native interface called by the VM.
 *
 */

typedef struct {
    const char* module_name;
    const char* module_version;
    const char* libpath;
    const char* source;
} jmodule;

#define JIGSAW_ERROR_INVALID_MODULE_LIBRARY       101
#define JIGSAW_ERROR_BAD_FILE_HEADER              102
#define JIGSAW_ERROR_BAD_CONFIG                   103
#define JIGSAW_ERROR_OPEN_CONFIG                  104
#define JIGSAW_ERROR_OPEN_MODULE_INFO             105
#define JIGSAW_ERROR_BAD_MODULE_INFO              106
#define JIGSAW_ERROR_INVALID_MODULE               107
#define JIGSAW_ERROR_INVALID_CONTEXT              108
#define JIGSAW_ERROR_MODULE_LIBRARY_NOT_FOUND     109
#define JIGSAW_ERROR_CONTEXTS_NOT_LOADED          110
#define JIGSAW_ERROR_MODULE_NOT_FOUND             111
#define JIGSAW_ERROR_BASE_MODULE_NOT_FOUND        112
#define JIGSAW_ERROR_CLASS_NOT_FOUND              113
#define JIGSAW_ERROR_READ_CLASS_ENTRY             114
#define JIGSAW_ERROR_INVALID_MODULE_IDS           116
#define JIGSAW_ERROR_ZIP_LIBRARY_NOT_FOUND        201
#define JIGSAW_ERROR_BUFFER_TOO_SHORT             202

/*
 * Return the path of the default system module library of the given java_home.
 * This method returns 0 if succeed; otherwise returns non-zero error code.

 * java_home : JAVA_HOME
 * libpath   : allocated buffer to be set with the path
 *             of the system module library
 * len       : length of the allocated libpath buffer 
 */
typedef jint
(*get_system_module_library_fn_t)(const char *java_home,
                                  char *libpath,
                                  size_t len);

/*
 * Load the contexts of a given module query and set the
 * *context to the context containing the base module.
 * This method returns 0 if succeed; otherwise returns non-zero
 * error code.
 *
 * libpath      : module library path (must be non-NULL)
 * modulepath   : module path or NULL
 * module_query : module query in module mode or NULL in classpath mode
 * context      : To be set with the handle to the context containing
 *                the base module.  The returned context can contain
 *                one or more modules that are required to be loaded
 *                by the VM bootstrap class loader.
 */
typedef jint
(*load_module_context_fn_t)(const char *libpath, const char *modulepath,
                            const char *module_query,
                            void **context);

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
typedef jint
(*find_local_module_class_fn_t)(void  *context,
                                const char *classname,
                                void  **module,
                                jint *len);

/*
 * Reads bytestream of a given classname local in a given module
 * This method returns 0 if succeed; otherwise returns non-zero
 * error code.
 *
 * module     : handle to the module containing the class
 * classname  : fully-qualified class name (in UTF8 format)
 * buf        : an allocated buffer to store the class data
 * len        : length of the buffer
 */
typedef jint
(*read_local_module_class_fn_t)(void *module,
                                const char *classname,
                                unsigned char *buf,
                                jint size);

/*
 * Get the information about the given module.
 *
 * module    : handle to a module
 * minfo     : a pointer to struct for the module information.
 */
typedef jint (*get_module_info_fn_t)(void *module,
                                     jmodule *minfo);

/*
 * Called by Java_org_openjdk_jigsaw_ClassPathContext_initBootstrapContexts
 */
void init_bootstrap_contexts(const char** non_bootstrap_modules, jint len);

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif /* JIGSAW_H */
