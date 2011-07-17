/*
 * Copyright (C) 2011 Klaus Reimer <k@ailis.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.ailis.maven.plugin.javascript;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;

/**
 * Copy resources for the main source code to the main output directory. Always
 * uses the project.build.resources element to specify the resources to copy.
 *
 * This is a 99% copy of the original ResourcesMojo. Extending an existing Mojo
 * is pretty difficult so I decided to copy the whole source and tweak the few
 * parts I need to change. Up to now the following changes have been applied:
 *
 * <ul>
 * <li>Changed the output directory</li>
 * <li>Cleaned up some generics</li>
 * <li>Added some more JavaDocs</li>
 * </ul>
 *
 * @author <a href="michal.maczka@dimatics.com">Michal Maczka</a>
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @author Andreas Hoheneder
 * @author William Ferguson
 * @author Klaus Reimer (k@ailis.de)
 * @goal resources
 * @phase process-resources
 * @threadSafe
 */
public class JsResourcesMojo extends AbstractMojo implements Contextualizable
{
    /**
     * The character encoding scheme to be applied when filtering resources.
     *
     * @parameter expression="${encoding}"
     *            default-value="${project.build.sourceEncoding}"
     */
    protected String encoding;

    /**
     * The output directory into which to copy the resources.
     *
     * @parameter
     *            default-value="${project.build.outputDirectory}/script-resources"
     * @required
     * @readonly
     */
    private File outputDirectory;

    /**
     * The list of resources we want to transfer.
     *
     * @parameter default-value="${project.resources}"
     * @required
     * @readonly
     */
    private List<Resource> resources;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The list of additional filter properties files to be used along with
     * System and project properties, which would be used for the filtering. <br/>
     * See also: {@link JsResourcesMojo#filters}.
     *
     * @parameter default-value="${project.build.filters}"
     * @readonly
     */
    protected List<String> buildFilters;

    /**
     * The list of extra filter properties files to be used along with System
     * properties, project properties, and filter properties files specified in
     * the POM build/filters section, which should be used for the filtering
     * during the current mojo execution. <br/>
     * Normally, these will be configured from a plugin's execution section, to
     * provide a different set of filters for a particular execution. For
     * instance, starting in Maven 2.2.0, you have the option of configuring
     * executions with the id's <code>default-resources</code> and
     * <code>default-testResources</code> to supply different configurations for
     * the two different types of resources. By supplying
     * <code>extraFilters</code> configurations, you can separate which filters
     * are used for which type of resource.
     *
     * @parameter
     */
    protected List<String> filters;

    /**
     * If false, don't use the filters specified in the build/filters section of
     * the POM when processing resources in this mojo execution. <br/>
     * See also: {@link JsResourcesMojo#buildFilters} and
     * {@link JsResourcesMojo#filters}
     *
     * @parameter default-value="true"
     */
    protected boolean useBuildFilters;

    /**
     *
     * @component
     *            role="org.apache.maven.shared.filtering.MavenResourcesFiltering"
     *            role-hint="default"
     * @required
     */
    protected MavenResourcesFiltering mavenResourcesFiltering;

    /**
     * @parameter default-value="${session}"
     * @readonly
     * @required
     */
    protected MavenSession session;

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
     * Additional file extensions to not apply filtering (already defined are :
     * jpg, jpeg, gif, bmp, png)
     *
     * @parameter
     */
    protected List<String> nonFilteredFileExtensions;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     *
     * @parameter expression="${maven.resources.escapeWindowsPaths}"
     *            default-value="true"
     */
    protected boolean escapeWindowsPaths;

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
     * <p>
     * List of plexus components hint which implements
     * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)}.
     * They will be executed after the resources copying/filtering.
     * </p>
     *
     * @parameter
     */
    private List<String> mavenFilteringHints;

    /**
     * The plexus container.
     */
    private PlexusContainer plexusContainer;

    /**
     * The maven filtering components.
     */
    private final List<Object> mavenFilteringComponents =
        new ArrayList<Object>();

    /**
     * stop searching endToken at the end of line
     *
     * @parameter expression="${maven.resources.supportMultiLineFiltering}"
     *            default-value="false"
     */
    private boolean supportMultiLineFiltering;

    /**
     * @see org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable#contextualize(org.codehaus.plexus.context.Context)
     */
    @Override
    public void contextualize(final Context context)
        throws ContextException
    {
        getLog().debug("execute contextualize");
        this.plexusContainer =
            (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute()
        throws MojoExecutionException
    {
        try
        {

            if (StringUtils.isEmpty(this.encoding)
                && isFilteringEnabled(getResources()))
            {
                getLog().warn(
                    "File encoding has not been set, using platform encoding "
                        + ReaderFactory.FILE_ENCODING
                        + ", i.e. build is platform dependent!");
            }

            final List<String> filters = getCombinedFiltersList();

            final MavenResourcesExecution mavenResourcesExecution =
                new MavenResourcesExecution(getResources(),
                    getOutputDirectory(),
                    this.project, this.encoding, filters,
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

    /**
     * Returns the combined list of filter property files.
     *
     * @return The combined list of filter property files.
     */
    protected List<String> getCombinedFiltersList()
    {
        if (this.filters == null || this.filters.isEmpty())
        {
            return this.useBuildFilters ? this.buildFilters : null;
        }
        else
        {
            final List<String> result = new ArrayList<String>();

            if (this.useBuildFilters && this.buildFilters != null
                && !this.buildFilters.isEmpty())
            {
                result.addAll(this.buildFilters);
            }

            result.addAll(this.filters);

            return result;
        }
    }

    /**
     * Determines whether filtering has been enabled for any resource.
     *
     * @param resources
     *            The set of resources to check for filtering, may be
     *            <code>null</code>.
     * @return <code>true</code> if at least one resource uses filtering,
     *         <code>false</code> otherwise.
     */
    private boolean isFilteringEnabled(final Collection<Resource> resources)
    {
        if (resources != null)
        {
            for (final Iterator<Resource> i = resources.iterator(); i.hasNext();)
            {
                final Resource resource = i.next();
                if (resource.isFiltering())
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the resources we want to transfer.
     *
     * @return The resources we want to transfer.
     */
    public List<Resource> getResources()
    {
        return this.resources;
    }

    /**
     * Sets the resources we want to transfer.
     *
     * @param resources
     *            The resources to set.
     */
    public void setResources(final List<Resource> resources)
    {
        this.resources = resources;
    }

    /**
     * Returns the output directory into which to copy the resources.
     *
     * @return The output directory.
     */
    public File getOutputDirectory()
    {
        return this.outputDirectory;
    }

    /**
     * Sets the output directory into which to copy the resources.
     *
     * @param outputDirectory
     *            The output directory to set.
     */
    public void setOutputDirectory(final File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the flag which indicates overwriting existing files even if the
     * destination files are newer.
     *
     * @return True if overwriting is enabled, false if disabled.
     */
    public boolean isOverwrite()
    {
        return this.overwrite;
    }

    /**
     * Enables or disables overwriting existing files even if the destination
     * files are newer.
     *
     * @param overwrite
     *            True to enable overwriting, false to disable.
     */
    public void setOverwrite(final boolean overwrite)
    {
        this.overwrite = overwrite;
    }

    /**
     * Checks if copying empty directories included is enabled.
     *
     * @return True if copying empty directories is enabled, false if disables.
     */
    public boolean isIncludeEmptyDirs()
    {
        return this.includeEmptyDirs;
    }

    /**
     * Enables or disables inclusion of empty directories.
     *
     * @param includeEmptyDirs
     *            True to enable the inclusion of empty directories, false to
     *            disable it.
     */
    public void setIncludeEmptyDirs(final boolean includeEmptyDirs)
    {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    /**
     * Return the list of filter property files.
     *
     * @return The list of filter property files.
     */
    public List<String> getFilters()
    {
        return this.filters;
    }

    /**
     * Sets the list of filter property files.
     *
     * @param filters
     *            The list of filter property files to set.
     */
    public void setFilters(final List<String> filters)
    {
        this.filters = filters;
    }

    /**
     * Returns the list of delimiters.
     *
     * @return The list of delimiters.
     */
    public List<String> getDelimiters()
    {
        return this.delimiters;
    }

    /**
     * Sets the list of delimiters.
     *
     * @param delimiters
     *            The list of delimiters to set.
     */
    public void setDelimiters(final List<String> delimiters)
    {
        this.delimiters = delimiters;
    }

    /**
     * Checks if default delimiters are enabled.
     *
     * @return True if default delimters are enabled, false if not.
     */
    public boolean isUseDefaultDelimiters()
    {
        return this.useDefaultDelimiters;
    }

    /**
     * Enables or disables default delimiters.
     *
     * @param useDefaultDelimiters
     *            True to enable default delimiters, false to disable them.
     */
    public void setUseDefaultDelimiters(final boolean useDefaultDelimiters)
    {
        this.useDefaultDelimiters = useDefaultDelimiters;
    }

}
