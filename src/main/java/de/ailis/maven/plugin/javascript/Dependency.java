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

import java.io.Serializable;

/**
 * Dependency.
 *
 * @author Klaus Reimer (k@ailis.de)
 */
public final class Dependency implements Serializable
{
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** The filename. */
    private final String filename;

    /** If dependency is required. */
    private final boolean required;

    /**
     * Constructor.
     *
     * @param filename
     *            The filename.
     * @param required
     *            True if dependency is required, false if it is only used.
     */
    public Dependency(final String filename, final boolean required)
    {
        this.filename = filename;
        this.required = required;
    }

    /**
     * Returns the filename.
     *
     * @return The filename.
     */
    public String getFilename()
    {
        return this.filename;
    }

    /**
     * Checks if this dependency is required or if it is only used.
     *
     * @return True if required, false if only used.
     */
    public boolean isRequired()
    {
        return this.required;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        if (this.required)
            return "@require " + this.filename;
        else
            return "@use " + this.filename;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result =
            prime * result
                + ((this.filename == null) ? 0 : this.filename.hashCode());
        result = prime * result + (this.required ? 1231 : 1237);
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final Dependency other = (Dependency) obj;
        if (this.filename == null)
        {
            if (other.filename != null) return false;
        }
        else if (!this.filename.equals(other.filename)) return false;
        if (this.required != other.required) return false;
        return true;
    }
}
