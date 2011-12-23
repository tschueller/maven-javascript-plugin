/*
 * Copyright (C) 2011 Klaus Reimer <k@ailis.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.ailis.maven.plugin.javascript;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.FileUtils;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.javascript.jscomp.SourceFile.Generator;


/**
 * This source generator adds a suppression annotation to the read source code
 * to suppress all warnings and errors for this file.
 * 
 * @author Klaus Reimer (k@ailis.de)
 */
public class SuppressedCodeGenerator implements Generator
{
    /** The pattern to find a fileoverview annotation in the file. */
    private final static Pattern FILEOVERVIEW_PATTERN = Pattern.compile("@fileoverview\\b");
            
    /** The suppression string to add to all generated sources. */
    private final static String SUPPRESSIONS = "@fileoverview\n@suppress {" +
        "accessControls|" +
        "checkRegExp|" +
        "checkTypes|" +
        "checkVars|" +
        "const|" +
        "constantProperty|" +
        "deprecated|" +
        "duplicate|" +
        "es5Strict|" +
        "extraProvide|" +
        "extraRequire|" +
        "fileoverviewTags|" +
        "globalThis|" +
        "invalidCasts|" +
        "missingProperties|" +
        "missingProvide|" +
        "missingRequire|" +
        "nonStandardJsDocs|" +
        "strictModuleDepCheck|" +
        "undefinedVars|" +
        "underscore|" +
        "unknownDefines|" +
        "uselessCode|" +
        "visibility|" +
        "with" +
        "}\n";

    /** The original source code. */
    private final String origCode;
    
    /**
     * Constructs a suppressed code generator reading the source code from the
     * specified input stream. The stream is NOT closed automatically by this
     * class and must be closed manually (You can do this right after calling
     * this constructor).
     * 
     * @param stream
     *            The input stream. Must be closed manually.
     * @throws IOException
     *             When stream read failed.
     */
    public SuppressedCodeGenerator(final InputStream stream) throws IOException
    {
        this.origCode =
            CharStreams.toString(new InputStreamReader(stream,
                Charsets.UTF_8));
    }

    /**
     * Constructs a suppressed code generator reading the source code from the
     * specified file.
     * 
     * @param file
     *            The input file.
     * @throws IOException
     *             When file read failed.
     */
    public SuppressedCodeGenerator(final File file) throws IOException
    {
        this.origCode = FileUtils.fileRead(file, "UTF-8");
    }

    /**
     * @see com.google.javascript.jscomp.SourceFile.Generator#getCode()
     */
    @Override
    public String getCode()
    {
        Matcher matcher = FILEOVERVIEW_PATTERN.matcher(this.origCode);
        if (matcher.find())
        {
            return matcher.replaceFirst(SUPPRESSIONS);
        }
        else
        {
            return "/**" + SUPPRESSIONS + "*/" + this.origCode;
        }
    }
}
