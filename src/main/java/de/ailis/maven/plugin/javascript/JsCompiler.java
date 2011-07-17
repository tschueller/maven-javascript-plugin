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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerError;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.DirectoryScanner;

import com.google.common.collect.Lists;
import com.google.common.io.LimitInputStream;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;

/**
 * The JavaScript compiler. This class implements the Plexus compiler interface
 * and wraps the Closure Compiler which does the real compilation.
 *
 * @author Klaus Reimer (k@ailis.de)
 */
public class JsCompiler extends AbstractLogEnabled implements Compiler
{
    /**
     * @see Compiler#canUpdateTarget(CompilerConfiguration)
     */
    @Override
    public boolean canUpdateTarget(final CompilerConfiguration configuration)
        throws CompilerException
    {
        return true;
    }

    /**
     * @see Compiler#compile(CompilerConfiguration)
     */
    @Override
    public List<CompilerError>
        compile(final CompilerConfiguration config)
            throws CompilerException
    {
        final List<CompilerError> messages = new ArrayList<CompilerError>();

        // Make sure we only have one source location because this compiler
        // can't cope with multiple source locations. Multiple sources must
        // be filtered into the "script-sources" directory anyway before
        // calling this compiler.
        final List<String> sourceLocations = config.getSourceLocations();
        if (sourceLocations.size() != 1)
            throw new CompilerException("Must have exactly one source location");
        final String sourceLocation = sourceLocations.get(0);

        // Find externs and sources.
        final List<JSSourceFile> externs = getExterns(config);
        final List<JSSourceFile> sources = getSources(config);

        // Initialize the dependency manager.
        final DependencyManager dependencyManager =
            new DependencyManager(new File(sourceLocation));
        try
        {
            dependencyManager.addScripts(sources);
        }
        catch (final IOException e)
        {
            throw new CompilerException("Unable to read source file: " + e, e);
        }

        // Sort source files according to automatically detected order.
        // Execution is stopped when a circular reference is detected.
        try
        {
            Collections.sort(sources,
                new OrderComparator(dependencyManager.resolve()));
        }
        catch (final CircularDependencyException e)
        {
            messages.add(new CompilerError(e.getMessage(), true));
            return messages;
        }

        // Write the uncompiled sources.
        writeSources(sources, dependencyManager, config);

        // Compile the sources
        if (getLogger() != null) getLogger().info(
            "Compiling " + sources.size() + " file(s) with "
                + externs.size() + " extern(s)");
        com.google.javascript.jscomp.Compiler.setLoggingLevel(Level.OFF);
        final ClosureCompiler compiler = new ClosureCompiler(getLogger(),
            config);
        if (sources.size() > 0)
        {
            final Result result = compiler.compile(externs, sources);
            for (final JSError error : result.errors)
                messages.add(new CompilerError(error.sourceName
                    .substring(sourceLocation.length() + 1), true,
                    error.lineNumber, error.getCharno(), error.lineNumber,
                    error.getCharno(), error.description));
            for (final JSError warning : result.warnings)
                messages.add(new CompilerError(warning.sourceName
                    .substring(sourceLocation.length() + 1), false,
                    warning.lineNumber, warning.getCharno(),
                    warning.lineNumber, warning.getCharno(),
                    warning.description));
            if (result.success)
            {
                final String source = compiler.toSource();
                writeResult(source, dependencyManager, config);
                final String[] compiledSources = compiler.toSourceArray();
                for (int i = 0; i < sources.size(); i++)
                {
                    writeSingleResult(sources.get(i), compiledSources[i],
                        dependencyManager.getDependencies(sources.get(i)),
                        config);
                }
            }
        }

        return messages;
    }

    /**
     * @see Compiler#createCommandLine(CompilerConfiguration)
     */
    @Override
    public String[] createCommandLine(final CompilerConfiguration config)
        throws CompilerException
    {
        return null;
    }

    /**
     * @see Compiler#getCompilerOutputStyle()
     */
    @Override
    public CompilerOutputStyle getCompilerOutputStyle()
    {
        return CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE;
    }

    /**
     * @see Compiler#getInputFileEnding(CompilerConfiguration)
     */
    @Override
    public String getInputFileEnding(final CompilerConfiguration configuration)
        throws CompilerException
    {
        return ".js";
    }

    /**
     * @see Compiler#getOutputFile(CompilerConfiguration)
     */
    @Override
    public String getOutputFile(final CompilerConfiguration configuration)
        throws CompilerException
    {
        throw new RuntimeException(
            "This compiler implementation doesn't have one output file for all files.");
    }

    /**
     * @see Compiler#getOutputFileEnding(CompilerConfiguration)
     */
    @Override
    public String
        getOutputFileEnding(final CompilerConfiguration configuration)
            throws CompilerException
    {
        return ".js";
    }

    /**
     * Returns the bundles directory.
     *
     * @param config
     *            The compiler configuration.
     * @return The bundles directory.
     */
    private File getBundlesDirectory(final CompilerConfiguration config)
    {
        return new File(config.getOutputLocation(), "script-bundles");
    }

    /**
     * Gets the default externs set.
     *
     * @return The default externs.
     * @throws CompilerException
     *             When an exception occurs during processing of externs.
     */
    private List<JSSourceFile> getDefaultExterns()
        throws CompilerException
    {
        try
        {
            final InputStream input =
                com.google.javascript.jscomp.Compiler.class
                    .getResourceAsStream("/externs.zip");
            final ZipInputStream zip = new ZipInputStream(input);
            final List<JSSourceFile> externs = Lists.newLinkedList();

            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;)
            {
                final LimitInputStream entryStream = new LimitInputStream(zip,
                    entry
                        .getSize());
                externs.add(JSSourceFile.fromInputStream(entry.getName(),
                    entryStream));
            }

            return externs;
        }
        catch (final IOException e)
        {
            throw new CompilerException(e.toString(), e);
        }
    }

    /**
     * Searches for extern files.
     *
     * @param config
     *            The compiler configuration.
     * @return The extern files.
     * @throws CompilerException
     *             If an exception occurs while searching for extern files.
     */
    private List<JSSourceFile> getExterns(final CompilerConfiguration config)
        throws CompilerException
    {
        final List<JSSourceFile> externs = new ArrayList<JSSourceFile>();

        externs.addAll(getDefaultExterns());

        final String outputLocation = config.getOutputLocation();
        for (final String entry : (List<String>) config.getClasspathEntries())
        {
            // Ignore our own classpath entry
            if (entry.equals(outputLocation)) continue;

            final File file = new File(entry);
            // this.searchPath.add(file);
            if (file.isDirectory())
            {
                File externDir = new File(file, "script-externs");
                if (!externDir.exists())
                    externDir = new File(file, "script-source-bundles");
                if (!externDir.exists()) continue;
                for (final File externFile : externDir.listFiles())
                {
                    final JSSourceFile source =
                        JSSourceFile.fromFile(externFile);
                    externs.add(source);
                }
            }
            else
            {
                try
                {
                    final JarFile jarFile = new JarFile(file);
                    final Enumeration<JarEntry> jarEntries = jarFile.entries();
                    while (jarEntries.hasMoreElements())
                    {
                        final JarEntry jarEntry = jarEntries.nextElement();
                        if (jarEntry.isDirectory()) continue;
                        final String entryName = jarEntry.getName();
                        if (entryName.startsWith("scripts-externs/")
                            || entryName.startsWith("scripts-source-bundles/"))
                        {
                            final InputStream stream =
                                jarFile.getInputStream(jarEntry);
                            try
                            {
                                externs.add(JSSourceFile.fromInputStream(
                                    file.getAbsolutePath() + ":" + entryName,
                                    stream));
                            }
                            finally
                            {
                                stream.close();
                            }
                        }
                    }
                }
                catch (final IOException e)
                {
                    throw new CompilerException(e.toString(), e);
                }
            }
        }
        return externs;
    }

    /**
     * Returns the scripts directory.
     *
     * @param config
     *            The compiler configuration.
     * @return The scripts directory.
     */
    private File getScriptsDirectory(final CompilerConfiguration config)
    {
        return new File(config.getOutputLocation(), "scripts");
    }

    /**
     * Returns the source bundles directory.
     *
     * @param config
     *            The compiler configuration.
     * @return The source bundles directory.
     */
    private File getSourceBundlesDirectory(final CompilerConfiguration config)
    {
        return new File(config.getOutputLocation(), "script-source-bundles");
    }

    /**
     * Returns the source files to compile.
     *
     * @param config
     *            The compiler configuration.
     * @return The source files to compile.
     */
    private List<JSSourceFile> getSources(final CompilerConfiguration config)
    {
        final List<JSSourceFile> sources =
            new ArrayList<JSSourceFile>();

        for (final String sourceLocation : (List<String>) config
            .getSourceLocations())
        {
            final DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(sourceLocation);

            final Set<String> includes = config.getIncludes();
            final Set<String> excludes = config.getExcludes();

            if (includes.isEmpty())
                scanner.setIncludes(new String[] { "**/*.js" });
            else
                scanner.setIncludes(includes.toArray(new String[0]));
            scanner.addDefaultExcludes();
            if (!excludes.isEmpty())
                scanner.setExcludes(excludes.toArray(new String[0]));
            scanner.scan();
            final List<String> files =
                Arrays.asList(scanner.getIncludedFiles());

            // Build the list with the JavaScript source files.
            for (final String file : files)
            {
                final File sourceFile = new File(sourceLocation, file);
                if (sourceFile.exists())
                {
                    final JSSourceFile source =
                        JSSourceFile.fromFile(sourceFile);
                    source.setOriginalPath(file);
                    sources.add(source);
                }
            }
        }

        return sources;
    }

    /**
     * Write the compilation results to the target file.
     *
     * @param source
     *            The source.
     * @param dependencyManager
     *            The script dependency manager.
     * @param config
     *            The compiler configuration.
     * @throws CompilerException
     *             When an exception occurs while writing the result.
     */

    private void writeResult(final String source,
        final DependencyManager dependencyManager,
        final CompilerConfiguration config)
        throws CompilerException
    {
        final String bundleFilename = config.getOutputFileName();

        final File output =
            new File(getBundlesDirectory(config), bundleFilename);
        output.getParentFile().mkdirs();
        try
        {
            final OutputStreamWriter out =
                new OutputStreamWriter(new FileOutputStream(output), "UTF-8");
            out.append(source);

            final Set<Dependency> dependencies =
                dependencyManager.getExternalDependencies();
            final Set<String> provides =
                dependencyManager.getProvidedDependencies();
            if (dependencies.size() > 0 || provides.size() > 0)
            {
                out.append("\n/*\n");
                for (final Dependency dependency : dependencies)
                {
                    out.append(" * ");
                    out.append(dependency.toString());
                    out.append('\n');
                }
                for (final String provide : provides)
                {
                    out.append(" * %provide ");
                    out.append(provide);
                    out.append('\n');
                }
                out.append(" */\n");
            }
            out.close();
        }
        catch (final IOException e)
        {
            throw new CompilerException(e.toString(), e);
        }
    }

    /**
     * Writes the result of a single file.
     *
     * @param jsSourceFile
     *            The source file
     * @param compiledSource
     *            The compiled source
     * @param dependencies
     *            The dependencies of this source file.
     * @param config
     *            The compiler configuration.
     * @throws CompilerException
     *             If result could not be written.
     */

    private void writeSingleResult(final JSSourceFile jsSourceFile,
        final String compiledSource, final List<Dependency> dependencies,
        final CompilerConfiguration config)
        throws CompilerException
    {
        final File output =
            new File(getScriptsDirectory(config),
                jsSourceFile.getOriginalPath());
        output.getParentFile().mkdirs();

        try
        {
            final OutputStreamWriter out =
                new OutputStreamWriter(new FileOutputStream(output), "UTF-8");
            out.append(compiledSource);
            if (dependencies.size() > 0)
            {
                out.append("\n/*\n");
                for (final Dependency dependency : dependencies)
                {
                    out.append(" * ");
                    out.append(dependency.toString());
                    out.append('\n');
                }
                out.append(" */\n");
            }
            out.close();
        }
        catch (final IOException e)
        {
            throw new CompilerException(e.toString(), e);
        }
    }

    /**
     * Write the uncompiled source file.
     *
     * @param sources
     *            The sources.
     * @param dependencyManager
     *            The script dependency manager
     * @param config
     *            The compiler configuration.
     * @throws CompilerException
     *             When an exception occurs while writing the sources.
     */
    private void writeSources(final List<JSSourceFile> sources,
        final DependencyManager dependencyManager,
        final CompilerConfiguration config)
        throws CompilerException
    {
        final File output =
            new File(getSourceBundlesDirectory(config),
                config.getOutputFileName());
        output.getParentFile().mkdirs();

        try
        {
            final OutputStreamWriter out =
                new OutputStreamWriter(new FileOutputStream(output), "UTF-8");
            // out.append("\n/**\n");
            // out.append(" * @fileoverview\n");
            // out.append(" * @suppress {accessControls|checkRegExp|checkTypes|checkVars|deprecated|fileoverviewTags|invalidCasts|missingProperties|nonStandardJsDocs|strictModuleDepCheck|undefinedVars|unknownDefines|uselessCode|visibility}\n");
            // out.append(" */\n\n");
            for (final JSSourceFile source : sources)
            {
                final String filename = source.getOriginalPath();

                // Add source to bundle file
                out.append("// ===========================================================================\n");
                out.append("// " + filename + "\n");
                out.append("// ===========================================================================\n");
                out.append("\n");
                out.append(source
                    .getCode()
                    .replaceAll("(?m)^\\s*\\*\\s*@fileoverview", "")
                    .replaceAll(
                        "(?m)^\\s*\\*\\s*@(use|require)\\s+[a-zA-Z0-9\\/\\-\\.]+\\s*$[\n\r]*",
                        ""));
                out.append("\n\n");
            }

            final Set<String> provides =
                dependencyManager.getProvidedDependencies();
            final Set<Dependency> dependencies =
                dependencyManager.getExternalDependencies();
            if (dependencies.size() > 0 || provides.size() > 0)
            {
                out.append("\n/*\n");
                for (final Dependency dependency : dependencies)
                {
                    out.append(" * ");
                    out.append(dependency.toString());
                    out.append('\n');
                }
                for (final String provide : provides)
                {
                    out.append(" * @provide ");
                    out.append(provide);
                    out.append('\n');
                }
                out.append(" */\n");
            }

            out.close();
        }
        catch (final IOException e)
        {
            throw new CompilerException(e.toString(), e);
        }
    }
}
