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

import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;

/**
 * Closure Compiler error manager linked to a Maven logger and build context.
 *
 * @author Klaus Reimer (k@ailis.de)
 */
public class MavenErrorManager extends BasicErrorManager
{
    /** The build context. */
    private final BuildContext buildContext;

    /** The Plexus logger. */
    private final Log log;

    /**
     * Constructor
     *
     * @param log
     *            The Plexus logger.
     * @param buildContext
     *            The build context.
     */
    public MavenErrorManager(final Log log, final BuildContext buildContext)
    {
        this.buildContext = buildContext;
        this.log = log;
    }

    /**
     * @see BasicErrorManager#println(CheckLevel, JSError)
     */
    @Override
    public void println(final CheckLevel level, final JSError error)
    {
        switch (level)
        {
            case OFF:
                break;

            case ERROR:
                this.buildContext.addMessage(new File(error.sourceName),
                    error.lineNumber,
                    error.getCharno(), error.description,
                    BuildContext.SEVERITY_ERROR, null);
                break;
            case WARNING:
                this.buildContext.addMessage(new File(error.sourceName),
                    error.lineNumber,
                    error.getCharno(), error.description,
                    BuildContext.SEVERITY_WARNING, null);
                break;
        }
    }

    /**
     * @see BasicErrorManager#printSummary()
     */
    @Override
    protected void printSummary()
    {
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
}
