/*
 * Copyright (C) 2011 Klaus Reimer <k@ailis.de>
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.CompilationFailureException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerError;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * This Mojo processes the JavaScript files in the source directory, copies,
 * bundles and compiles them into specific folders in the output directory.
 * Compile dependencies are used as so-called externals for the Closure Compiler
 * which is used to compile the sources.
 *
 * @author Klaus Reimer (k@ailis.de)
 * @goal compile
 * @phase compile
 * @threadSafe
 * @requiresDependencyResolution compile
 */
public class JsCompilerMojo extends AbstractMojo
{
    /** Separator string for log messages. */
    private final static String SEPARATOR =
        "-------------------------------------------------------------";

    /**
     * The character encoding scheme to be applied when filtering resources.
     *
     * @parameter expression="${encoding}"
     *            default-value="${project.build.sourceEncoding}"
     */
    protected String encoding;

    /**
     * Indicates whether the build will continue even if there are compilation
     * errors; defaults to true.
     *
     * @parameter expression="${maven.compiler.failOnError}"
     *            default-value="true"
     */
    private final boolean failOnError = true;

    /**
     * Set to true to show messages about what the compiler is doing.
     *
     * @parameter expression="${maven.compiler.verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * The source directories containing the sources to be compiled.
     *
     * @parameter default-value="${project.build.sourceDirectory}"
     * @required
     * @readonly
     */
    private File sourceDirectory;

    /**
     * The output filename of the bundle file.
     *
     * @parameter expression="${project.artifactId}.js"
     * @required
     */
    private String bundleFilename;

    /**
     * The output directory for the uncompressed script files.
     *
     * @parameter expression="${project.build.outputDirectory}/script-sources"
     * @required
     * @readonly
     */
    private File sourceOutputDirectory;

    /**
     * Project classpath.
     *
     * @parameter default-value="${project.compileClasspathElements}"
     * @required
     * @readonly
     */
    private List<String> classpathElements;

    /**
     * The output directory for the compiled scripts.
     *
     * @parameter default-value="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File outputDirectory;

    /**
     * A list of inclusion filters for the compiler.
     *
     * @parameter
     */
    protected String[] includes;

    /**
     * A list of exclusion filters for the compiler.
     *
     * @parameter
     */
    protected String[] excludes;

    /**
     * Enables source code filtering.
     *
     * @parameter default-value="true"
     */
    protected boolean filtering;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These
     * delimiters are specified in the form 'beginToken*endToken'. If no '*' is
     * given, the delimiter is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     *
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt/delimiter&gt;
     *   &lt;delimiter&gt;@&lt/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the '@' delimiter is the same on both ends, we don't need to
     * specify '@*@' (though we can).
     * </p>
     *
     * @parameter
     */
    protected List<String> delimiters;

    /**
     * @parameter default-value="true"
     */
    protected boolean useDefaultDelimiters;

    /**
     * The maven project.
     *
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter default-value="${session}"
     * @readonly
     * @required
     */
    protected MavenSession session;

    /**
     * The list of extra filter properties files to be used.
     *
     * @parameter
     */
    protected List<String> filters;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     *
     * @parameter expression="${maven.resources.escapeWindowsPaths}"
     *            default-value="true"
     */
    protected boolean escapeWindowsPaths;

    /**
     * Expression preceded with the String won't be interpolated \${foo} will be
     * replaced with ${foo}
     *
     * @parameter expression="${maven.resources.escapeString}"
     */
    protected String escapeString;

    /**
     * Overwrite existing files even if the destination files are newer.
     *
     * @parameter expression="${maven.resources.overwrite}"
     *            default-value="false"
     */
    private boolean overwrite;

    /**
     * Copy any empty directories included in the Ressources.
     *
     * @parameter expression="${maven.resources.includeEmptyDirs}"
     *            default-value="false"
     */
    protected boolean includeEmptyDirs;

    /**
     * stop searching endToken at the end of line
     *
     * @parameter expression="${maven.resources.supportMultiLineFiltering}"
     *            default-value="false"
     */
    protected boolean supportMultiLineFiltering;

    /**
     * Additional file extensions to not apply filtering (already defined are :
     * jpg, jpeg, gif, bmp, png)
     *
     * @parameter
     */
    protected List<String> nonFilteredFileExtensions;

    /**
     * The JavaScript compiler.
     *
     * @component hint="jscompiler"
     */
    private Compiler compiler;

    /**
     *
     * @component
     *            role="org.apache.maven.shared.filtering.MavenResourcesFiltering"
     *            role-hint="default"
     * @required
     */
    protected MavenResourcesFiltering mavenResourcesFiltering;

    /**
     * <p>
     * List of plexus components hint which implements
     * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)}.
     * They will be executed after the resources copying/filtering.
     * </p>
     *
     * @parameter
     */
    protected List<String> mavenFilteringHints;

    /**
     * The maven filtering components.
     */
    private final List<Object> mavenFilteringComponents =
        new ArrayList<Object>();

    /**
     * The plexus container.
     */
    private PlexusContainer plexusContainer;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        // Output some debug information about the used directories.
        if (getLog().isDebugEnabled())
        {
            getLog().debug("Source directory: " + this.sourceDirectory);
            getLog().debug("Classpath: "
                + this.classpathElements.toString().replace(',', '\n'));
            getLog().debug("Output directory: " + this.outputDirectory);
        }

        // Copy/Filter sources from source directory to script source
        // output directory.
        copySources();

        // Setup the compiler configuration.
        final CompilerConfiguration config = new CompilerConfiguration();
        config.setOutputLocation(this.outputDirectory.getAbsolutePath());
        config.setClasspathEntries(this.classpathElements);
        config.setSourceLocations(Arrays.asList(new String[] {
            this.sourceOutputDirectory.getAbsolutePath() }));
        config.setSourceEncoding(this.encoding);
        if (this.includes != null) config.setIncludes(
            new HashSet<String>(Arrays.asList(this.includes)));
        if (this.excludes != null) config.setExcludes(
            new HashSet<String>(Arrays.asList(this.excludes)));
        config.setOutputFileName(this.bundleFilename);
        config.setVerbose(this.verbose);

        // Run the compiler.
        List<CompilerError> messages;
        try
        {
            messages = this.compiler.compile(config);
            if (messages == null) return;
        }
        catch (final CompilerException e)
        {
            throw new MojoFailureException(e.toString(), e);
        }

        // Evaluate the compiler messages.
        final List<CompilerError> warnings = new ArrayList<CompilerError>();
        final List<CompilerError> errors = new ArrayList<CompilerError>();
        for (final CompilerError message : messages)
            if (message.isError())
                errors.add(message);
            else
                warnings.add(message);

        if (this.failOnError && !errors.isEmpty())
        {
            if (!warnings.isEmpty())
            {
                getLog().info(SEPARATOR);
                getLog().warn("COMPILATION WARNING : ");
                getLog().info(SEPARATOR);
                for (final CompilerError warning : warnings)
                    getLog().warn(warning.toString());
                getLog().info(warnings.size() + ((warnings.size() > 1) ?
                    " warnings " : " warning"));
                getLog().info(SEPARATOR);
            }
            getLog().info(SEPARATOR);
            getLog().error("COMPILATION ERROR : ");
            getLog().info(SEPARATOR);
            for (final CompilerError error : errors)
                getLog().error(error.toString());
            getLog().info(errors.size() + ((errors.size() > 1) ? " errors " :
                " error"));
            getLog().info(SEPARATOR);
            throw new CompilationFailureException(errors);
        }
        else
            for (final CompilerError message : messages)
                getLog().warn(message.toString());
    }

    /**
     * Copies/filters the sources into the source output directory.
     *
     * @throws MojoExecutionException
     *             When execution fails.
     */
    private void copySources() throws MojoExecutionException
    {
        // Get the source folders
        final List<Resource> sourceFolders = getSourceFolders();

        final MavenResourcesExecution mavenResourcesExecution =
            new MavenResourcesExecution(sourceFolders,
                this.sourceOutputDirectory,
                this.project, this.encoding, this.filters,
                Collections.EMPTY_LIST,
                this.session);

        mavenResourcesExecution
            .setEscapeWindowsPaths(this.escapeWindowsPaths);

        // never include project build filters in this call, since we've
        // already accounted for the POM build filters
        // above, in getCombinedFiltersList().
        mavenResourcesExecution.setInjectProjectBuildFilters(false);

        mavenResourcesExecution.setEscapeString(this.escapeString);
        mavenResourcesExecution.setOverwrite(this.overwrite);
        mavenResourcesExecution.setIncludeEmptyDirs(this.includeEmptyDirs);
        mavenResourcesExecution
            .setSupportMultiLineFiltering(this.supportMultiLineFiltering);


        // if these are NOT set, just use the defaults, which are '${*}' and
        // '@'.
        if (this.delimiters != null && !this.delimiters.isEmpty())
        {
            final LinkedHashSet<String> delims =
                new LinkedHashSet<String>();
            if (this.useDefaultDelimiters)
            {

                delims.addAll(mavenResourcesExecution.getDelimiters());
            }

            for (final Iterator<String> dIt = this.delimiters.iterator(); dIt
                .hasNext();)
            {
                final String delim = dIt.next();
                if (delim == null)
                {
                    // FIXME: ${filter:*} could also trigger this condition.
                    // Need a better long-term solution.
                    delims.add("${*}");
                }
                else
                {
                    delims.add(delim);
                }
            }

            mavenResourcesExecution.setDelimiters(delims);
        }

        if (this.nonFilteredFileExtensions != null)
        {
            mavenResourcesExecution
                .setNonFilteredFileExtensions(this.nonFilteredFileExtensions);
        }
        try
        {
            this.mavenResourcesFiltering
                .filterResources(mavenResourcesExecution);
            executeUserFilterComponents(mavenResourcesExecution);
        }
        catch (final MavenFilteringException e)
        {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Returns the source files to copy/filter.
     *
     * @return The source files to copy/filter.
     */
    private List<Resource> getSourceFolders()
    {
        final List<Resource> sourceFolders = new ArrayList<Resource>();
        final Resource sourceFolder = new Resource();
        sourceFolder.setDirectory(this.sourceDirectory.getPath());
        if (this.includes != null)
            sourceFolder.setIncludes(Arrays.asList(this.includes));
        if (this.excludes != null)
            sourceFolder.setExcludes(Arrays.asList(this.excludes));
        sourceFolder.setFiltering(true);
        sourceFolders.add(sourceFolder);
        return sourceFolders;
    }

    /**
     * Executes user filter components.
     *
     * @param mavenResourcesExecution
     *            The maven resources execution.
     * @throws MojoExecutionException
     *             When component lookup fails.
     * @throws MavenFilteringException
     *             When something goes wrong while filtering.
     */
    protected void executeUserFilterComponents(
        final MavenResourcesExecution mavenResourcesExecution)
        throws MojoExecutionException, MavenFilteringException
    {
        if (this.mavenFilteringHints != null)
        {
            for (final Iterator<String> ite =
                this.mavenFilteringHints.iterator(); ite
                .hasNext();)
            {
                final String hint = ite.next();
                try
                {
                    this.mavenFilteringComponents
                        .add(this.plexusContainer.lookup(
                            MavenResourcesFiltering.class.getName(), hint));
                }
                catch (final ComponentLookupException e)
                {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
        else
        {
            getLog().debug("no use filter components");
        }

        if (this.mavenFilteringComponents != null
            && !this.mavenFilteringComponents.isEmpty())
        {
            getLog().debug("execute user filters");
            for (final Iterator<Object> ite =
                this.mavenFilteringComponents.iterator(); ite
                .hasNext();)
            {
                final MavenResourcesFiltering filter =
                    (MavenResourcesFiltering) ite.next();
                filter.filterResources(mavenResourcesExecution);
            }
        }
    }
}
