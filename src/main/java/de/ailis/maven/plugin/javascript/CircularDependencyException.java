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

/**
 * Thrown when a recursive dependency was detected.
 * 
 * @author Klaus Reimer (k@ailis.de)
 */
public class CircularDependencyException extends RuntimeException
{
    /** Serial version UID */
    private static final long serialVersionUID = 1L;

    /** The filename where the error was detected. */
    private String filename;

    /**
     * Constructor.
     * 
     * @param message
     *            The exception message.
     * @param filename
     *            The filename where the error was detected.
     */
    public CircularDependencyException(final String message, String filename)
    {
        super(message);
        this.filename = filename;
    }

    /**
     * Returns the filename where the error was detected.
     * 
     * @return The filename where the error was detected.
     */
    public String getFilename()
    {
        return this.filename;
    }
}
