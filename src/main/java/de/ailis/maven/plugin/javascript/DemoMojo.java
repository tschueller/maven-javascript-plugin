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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Writes the dynamic files needed to run project-internal demos.
 *
 * @author Klaus Reimer (k@ailis.de)
 * @goal demo
 * @phase compile
 * @requiresDependencyResolution compile
 */
public class DemoMojo extends AbstractMojo
{
    /**
     * The character encoding scheme to be applied when filtering resources.
     *
     * @parameter default-value="${project.build.sourceEncoding}"
     */
    private String encoding;

    /**
     * The output directory for the demo files.
     *
     * @parameter expression="${project.build.directory}/demo"
     * @required
     * @readonly
     */
    private File demoOutputDirectory;

    /**
     * Project classpath.
     *
     * @parameter default-value="${project.compileClasspathElements}"
     * @required
     * @readonly
     */
    private List<String> classpathElements;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        this.demoOutputDirectory.mkdirs();
        try
        {
            writeSearchPathPhp();
            writeResolverPhp();
        }
        catch (final IOException e)
        {
            throw new MojoExecutionException(
                "Unable to write demo files: " + e, e);
        }
    }

    /**
     * Writes the searchpath.php file to the demo directory.
     *
     * @throws IOException
     *             When searchpath.php file could not be written.
     */
    private void writeSearchPathPhp() throws IOException
    {
        final File cpFile =
            new File(this.demoOutputDirectory, "searchpath.php");
        final OutputStreamWriter writer =
            new OutputStreamWriter(new FileOutputStream(cpFile), getEncoding());
        try
        {
            writer.append("<?php\n\n");
            writer.append("return array(");
            boolean first = true;
            for (final String entry : this.classpathElements)
            {
                if (first)
                {
                    writer.append("\n");
                    first = false;
                }
                else
                    writer.append(",\n");
                writer.append("    \"");
                writer.append(entry.replace(File.separatorChar, '/'));
                writer.append("\"");
            }
            writer.append("\n);\n");
            writer.append("\n?>\n");
        }
        finally
        {
            writer.close();
        }
    }

    /**
     * Writes the resolver.php file into the demo directory.
     *
     * @throws IOException
     *             When resolver.php could not be written.
     */
    private void writeResolverPhp() throws IOException
    {
        File output = new File(this.demoOutputDirectory, "resolver.php");
        if (output.exists()) return;
        
        final InputStream stream =
            getClass().getResourceAsStream("/resolver.php");
        if (stream == null) throw new IOException("resolver.php not found");
        try
        {
            FileUtils.copyStreamToFile(new RawInputStreamFacade(stream),
                output);
        }
        finally
        {
            stream.close();
        }
    }
    
    /**
     * Returns the encoding used for writing output files.
     *
     * @return The used encoding. Never null.
     */
    private String getEncoding()
    {
        if (this.encoding != null) return this.encoding;
        return "UTF-8";
    }
}
