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

package org.openjdk.jigsaw;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map.Entry;
import org.openjdk.jigsaw.ModuleFile.ModuleFileHeader;
import org.openjdk.jigsaw.ModuleFile.SectionHeader;
import org.openjdk.jigsaw.ModuleFile.SubSectionFileHeader;

/**
 * <p> A parser of <a
 * href="http://cr.openjdk.java.net/~mr/jigsaw/notes/module-file-format/">
 * module files</a> </p>
 *
 * <p> The ModuleFileParser is designed to iterate over the module file using
 * {@code hasNext} and {@code next}. The section type and data can be
 * accessed using methods such as {@code getSectionHeader},
 * {@code getContentStream}, and {@code getClasses}. </p>
 *
 * <p> The {@code next} method causes the parser to read the next parse event,
 * and returns an {@code Event} which identifies the type of event just read.
 * The current event type can be determined invoking the {@code event}
 * method. </p>
 *
 * <p> Convenience methods, such as {@code skipToNextStartSection} and
 * {@code skipToNextStartSubSection}, are defined to support easy access to
 * (sub)section content without having to handle specific event types. </p>
 *
 * <p> A minimal example for using this API may look as follows:
 * <pre>
 *       try (FileInputStream fis = new FileInputStream(jmodFile)) {
 *           ModuleFileParser parser = ModuleFile.newParser(fis);
 *           while (parser.skipToNextStartSection()) {
 *               SectionHeader header = parser.getSectionHeader();
 *               SectionType type = header.getType();
 *               switch (type) {
 *                   case MODULE_INFO:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case SIGNATURE:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case CLASSES:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case RESOURCES:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case NATIVE_LIBS:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case NATIVE_CMDS:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   case CONFIG:
 *                       out.format("%s section, %s%n", type, header); break;
 *                   default:
 *                       throw new IOException("Unknown section type");
 *               }
 *           }
 *       }
 * </pre></p>
 */

public interface ModuleFileParser {

    /**
     * <p> {@link ModuleFileParser} parsing events </p>
     *
     * <p> Events must follow the following grammar.
     * <pre>
     *  START_FILE
     *    START_SECTION          (MODULE_INFO must be present)
     *    END_SECTION
     *    [START_SECTION         (optional sections)
     *       [START_SUBSECTION   (sections with subsections)
     *        END_SUBSECTION]+
     *     END_SECTION]+
     *  END_FILE
     * </pre> </p>
     */
    public static enum Event {
        /**
         * The first event returned. The module header has been consumed.
         */
        START_FILE,
        /**
         * The start of a section. The section header has been consumed.
         */
        START_SECTION,
        /**
         * The start of a subsection. The subsection header has been consumed.
         */
        START_SUBSECTION,
        /**
         * The end of a subsection. The subsection content has been consumed.
         */
        END_SUBSECTION,
        /**
         * The end of a section. The section content has been consumed.
         */
        END_SECTION,
        /**
         * The end of the module file. The file content has been consumed.
         */
        END_FILE;
    }

    /**
     * Returns the module file header.
     *
     * @return  The module file header
     */
    public ModuleFileHeader fileHeader();

    /**
     * Returns current event of the parser.
     */
    public Event event();

    /**
     * Check if there are more events.
     *
     * @return  true, if and only if, there are more events
     */
    public boolean hasNext();

    /**
     * Returns the next parsing event.
     *
     * <p> Skips over any unread data from a previous (sub)section. </p>
     *
     * @return  the next parse event
     *
     * @throws  NoSuchElementException
     *          If invoked when {@code hasNext} returns false
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing the underlying module file
     */
    public Event next();

    /**
     * Returns the header of the current section.
     *
     * @return  the section header
     *
     * @throws  ModuleFileParserException
     *          If the current event is one of {@code START_FILE} or
     *          {@code END_FILE}
     */
    public SectionHeader getSectionHeader();

    /**
     * Returns the header of the current file subsection.
     *
     * @return  the subsection header
     *
     * @throws  ModuleFileParserException
     *          If the current event is not one of {@code START_SUBSECTION} or
     *          {@code END_SUBSECTION}
     */
    public SubSectionFileHeader getSubSectionFileHeader();

    /**
     * Returns the hash of the module file header, current section, or file.
     *
     * <p> Returns the hash of the module file header if the current event is
     * {@code START_FILE}, the section hash if the current event is {@code
     * END_SECTION}, or the complete file contents hash if the current event is
     * {@code END_FILE}. </p>
     *
     * <p> The specific hashes are calculated as per the module-file
     * specification. More specifically, the module file header hash and the
     * section hash exclude the follow fields from their header, the hash length
     * and the hash value. The complete file contents hash excludes the file
     * hash value in the module file header and the signature section
     * (if present). </p>
     *
     * @return  the hash bytes.
     *
     * @throws  ModuleFileParserException
     *          If the current event is not one of {@code START_FILE}, {@code
     *          END_SECTION}, or {@code END_FILE}
     */
    public byte[] getHash();

    /**
     * Returns an InputStream of the uncompressed content of the current
     * section or subsection (if the section defines a compressor), otherwise
     * just the raw bytes.
     *
     * <p> For the {{@code CLASSES} section ( {@code getSectionHeader.getType() ==
     * ModuleFile.SectionType.CLASSES} ) the {@code getClasses} method should be
     * invoked. All other sections and subsections can invoke this method to get
     * the uncompressed (sub)section content. </p>
     *
     * @return  the content stream
     *
     * @throws  ModuleFileParserException
     *          If the current event is not one of {@code START_SECTION} or
     *          {@code START_SUBSECTION}, if {@code getSectionHeader().getType()}
     *          returns {@code ModuleFile.SectionType.CLASSES}, or if there is
     *          an error processing the underlying module file
     */
    public InputStream getContentStream();

    /**
     * Returns an Iterator over the classes in the {@code CLASSES} section.
     *
     * <p> If this method is invoked to extract the content of the classes
     * section then it must be invoked in a section whose
     * {@code getSectionHeader.getType()} returns
     * {@code ModuleFile.SectionType.CLASSES}. Any other time will throw a
     * {@code ModuleFileParserException}. </p>
     *
     * @return  an Iterator over the classes, where the entry key is the class
     *          file name, e.g. java/lang/Object.class, and the value is an
     *          input stream containing the class bytes.
     *
     * @throws  ModuleFileParserException
     *          If the current event is not {@code START_SECTION}, if
     *          {@code getSectionHeader().getType()} does not return
     *          {@code ModuleFile.SectionType.CLASSES}, or if there is an error
     *          processing the underlying module file
     */
    public Iterator<Entry<String,InputStream>> getClasses();

    /**
     * Returns an InputStream of the raw bytes of the current section or
     * subsection.
     *
     * <p> If the (sub)section data is compressed then this method simply
     * returns the compressed bytes, and the invoker is responsible for
     * decompressing. </p>
     *
     * @return  The raw (sub)section data
     *
     * @throws  ModuleFileParserException
     *          If the current event is not one of {@code START_SECTION} or
     *          {@code START_SUBSECTION}
     */
    public InputStream getRawStream();

    /**
     * Skips to the start of the next section.
     *
     * <p> Skips over any unread data, and consumes events until {@code
     * START_SECTION} or {@code END_FILE} is encountered. </p>
     *
     * @return  true, if and only if, the next event is {@code START_SECTION}
     *
     * @throws  ModuleFileParserException
     *          If there is an error processing the underlying module file
     */
    public boolean skipToNextStartSection();

    /**
     * Skips to the start of the next subsection.
     *
     * <p> Skips over any unread data, and consumes events until {@code
     * END_SECTION} is encountered. </p>
     *
     * @return  true, if and only if, the current event is {@code START_SUBSECTION}
     *
     * @throws  ModuleFileParserException
     *          If not within a section that contains subsections, or if there is
     *          an error processing the underlying module file
     */
    public boolean skipToNextStartSubSection();

}
