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

import org.codehaus.plexus.logging.Logger;

import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.MessageFormatter;


/**
 * Closure Compiler error manager linked to a Plexus logger.
 *
 * @author Klaus Reimer (k@ailis.de)
 */
public class PlexusErrorManager extends BasicErrorManager
{
    /** The Plexus logger. */
    private final Logger log;

    /** The message formatter. */
    private final MessageFormatter formatter;

    /** If the compilation summary should be printed. */
    private boolean printSummary = true;

    /** If compile messages should be printed. */
    private boolean printMessages = false;

    /** The source directory. */
    private final String sourceDirectory;

    /**
     * Constructor
     *
     * @param formatter
     *            The message formatter.
     * @param log
     *            The Plexus logger.
     * @param sourceDirectory
     *            The source directory.
     */
    public PlexusErrorManager(final MessageFormatter formatter,
        final Logger log,
        final File sourceDirectory)
    {
        this.formatter = formatter;
        this.log = log;
        this.sourceDirectory =
            sourceDirectory.getAbsolutePath() + File.separatorChar;
    }

    /**
     * @see BasicErrorManager#println(CheckLevel, JSError)
     */
    @Override
    public void println(final CheckLevel level, final JSError error)
    {
        if (!this.printMessages) return;

        switch (level)
        {
            case OFF:
                break;

            case ERROR:
                this.log.error(error.format(level, this.formatter).replace(
                    this.sourceDirectory, ""));
                break;
            case WARNING:
                this.log.warn(error.format(level, this.formatter).replace(
                    this.sourceDirectory, ""));
                break;
        }
    }

    /**
     * @see BasicErrorManager#printSummary()
     */
    @Override
    protected void printSummary()
    {
        if (!this.printSummary) return;

        String message = getErrorCount() + " error(s), " + getWarningCount()
            + " warning(s)";

        final double percent = getTypedPercent();
        if (percent > 0.0)
        {
            message += ", ";
            if (percent >= 100.0)
                message += "100";
            else
                message += String.format("%.2f", getTypedPercent());
            message += "% typed";
        }

        if (getErrorCount() + getWarningCount() == 0)
            this.log.info(message);
        else
            this.log.warn(message);
    }

    /**
     * Enables or disables the printing of the compilation summary.
     *
     * @param printSummary
     *            True to enable, false to disable
     */
    public void setPrintSummary(final boolean printSummary)
    {
        this.printSummary = printSummary;
    }

    /**
     * Enables or disabled the printing of messages.
     *
     * @param printMessages
     *            True to enable, false to disable.
     */
    public void setPrintMessages(final boolean printMessages)
    {
        this.printMessages = printMessages;
    }
}
