<?php
/**
 * Resource resolver
 * http://www.ailis.de/~k/projects/maven-javascript-plugin/
 *
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
class Resolver
{
    /** The singleton instance. */
    private static $instance = null;

    /** The resolve path. */
    private $resolvePath = null;

    /** The regular expression for matching require tags */
    private $REQUIRE_REGEX = "/@require\\s+([-_a-zA-Z0-9\\/\\.]*)/";

    /** The regular expression for matching use tags */
    private $USE_REGEX = "/@use\\s+([-_a-zA-Z0-9\\/\\.]*)/";

    /** The mime type mapping */
    private $MIMETYPE_MAP = array(
        "js" => "application/javascript",
        "png" => "image/png",
        "gif" => "image/gif",
        "jpeg" => "image/jpeg",
        "jpg" => "image/jpeg",
        "cab" => "vnd.ms-cab-compressed",
        "jar" => "application/java-archive",
        "xml" => "text/plain",
        "dtd" => "text/plain",
        "css" => "text/css"
    );

    /** The inclusion stack */
    private $stack = array();

    /** The already included files */
    private $included = array();

    /** Used scripts */
    private $used = array();

    /**
     * Constructor.
     */
    private function __construct()
    {
        $this->resolvePath = require("searchpath.php");
    }

    /**
     * Returns the singleton instance.
     *
     * @return The singleton instance.
     */
    public static function getInstance()
    {
        if (!Resolver::$instance) Resolver::$instance = new Resolver();
        return Resolver::$instance;
    }

    /**
     * Includes the specified script.
     *
     * @param string $filename The script to include
     */
    public function includeScript($filename)
    {
        // Ignore script if already included
        if (in_array($filename, $this->included)) return;

        // Check for circular dependencies
        if (in_array($filename, $this->stack))
            trigger_error("Circular Dependencies: " .
                implode(" := ", $this->stack) . " := $filename", E_USER_ERROR);
        array_push($this->stack, $filename);

        // Process the script
        $actualFilename = $this->processScript($filename);
        $src = basename($_SERVER["PHP_SELF"]) . "/" . $actualFilename;
        echo "<script type=\"text/javascript\" src=\"$src\"></script>\n";

        // Mark script as included
        $this->included[] = $filename;
        array_pop($this->stack);

        if (!count($this->stack)) $this->finish();
    }

    /**
     * Uses the specified script.
     *
     * @param string $filename The script to use
     */
    public function useScript($filename)
    {
        // Ignore script if already included
        if (in_array($filename, $this->included)) return;

        // Ignore script if already used
        if (in_array($filename, $this->used)) return;

        // Mark script as used
        $this->used[] = $filename;
    }

    /**
     * Finishes processing of files. This recursively processes all the
     * files which have been marked as "used".
     */
    public function finish()
    {
        if (!count($this->used)) return;
        $used = array_splice($this->used, 0, count($this->used));
        foreach ($used as $use)
        {
            $this->includeScript($use);
        }
        $this->finish();
    }

    /**
     * Processes the specified script.
     *
     * @param string $filename The script to process
     * @return string
     *             The actual found filename.
     */
    public function processScript($filename)
    {
        $actualFilename = "script-sources/$filename";
        $data = $this->readResource($actualFilename);
        if (is_null($data))
        {
            $actualFilename = "scripts/$filename";
            $data = $this->readResource($actualFilename);
        }
        if (is_null($data))
        {
            end($this->stack);
            $requirer = prev($this->stack);
            if ($requirer)
                trigger_error("Script '$filename' not found (required from " . $requirer . ")", E_USER_ERROR);
            else
                trigger_error("Script '$filename' not found", E_USER_ERROR);
        }
        if (preg_match_all($this->REQUIRE_REGEX, $data, &$matches))
        {
            for ($i = 0; $i < count($matches[0]); $i++)
            {
                $this->includeScript($matches[1][$i]);
            }
        }
        if (preg_match_all($this->USE_REGEX, $data, &$matches))
        {
            for ($i = 0; $i < count($matches[0]); $i++)
            {
                $this->useScript($matches[1][$i]);
            }
        }
        return $actualFilename;
    }

    /**
     * Sends the file with the specified url to the client. This method
     * checks for a valid path and forbids any access to files outside of
     * valid folders and for all files which have no mime mapping.
     *
     * @param url
     *            The file URL
     */
    public function sendResource($filename)
    {
        $filename = substr($filename, 1);

        // Determine mime-type. If mime-type is not found then forbid access
        $info = pathinfo($filename);
        $extension = isset($info["extension"]) ? $info["extension"] : "";
        if (isset($this->MIMETYPE_MAP[$extension]))
            $mimetype = $this->MIMETYPE_MAP[$extension];
        else
            $mimetype = null;

        // Reject request if it does not match the allowed patterns or if mime
        // type could not be retrieved
        if (!$mimetype || strpos($filename, "..") !== false ||
            !preg_match("/^(scripts|script-resources|scripts|script-bundles|script-source-bundles|script-sources)\\/[a-zA-Z0-9\\/\\.\\-_]+$/", $filename))
        {
            header($_SERVER["SERVER_PROTOCOL"]." 403 Forbidden");
            header("Content-Type: text/plain");
            echo "Forbidden";
            exit;
        }

        // Return the file
        $data = $this->readResource($filename);
        if (!is_null($data))
        {
            header("Content-Type: $mimetype");
            echo $data;
            exit;
        }

        header($_SERVER["SERVER_PROTOCOL"]." 404 Not found");
        header("Content-Type: text/plain");
        echo "Not found";
        exit;
    }

    /**
     * Searchs for the specified resource, reads it and returns the content.
     * If resource was not found then null is returned.
     */
    private function readResource($filename)
    {
        foreach ($this->resolvePath as $path)
        {
            if (is_dir($path))
            {
                $file = "$path/$filename";
                if (file_exists($file))
                {
                    return file_get_contents($file);
                }
            }
            else
            {
                $zip = zip_open($path);
                do
                {
                    $entry = zip_read($zip);
                }
                while ($entry && zip_entry_name($entry) != $filename);
                if ($entry)
                {
                    zip_entry_open($zip, $entry, "r");
                    $data = zip_entry_read($entry, zip_entry_filesize($entry));
                    zip_entry_close($entry);
                    zip_close($zip);
                    return $data;
                }
                zip_close($zip);
            }
        }
        return null;
    }

    /**
     * Processes the request.
     */
    public function processRequest()
    {
        // If no path info is present then ignore the request
        if (!isset($_SERVER["PATH_INFO"])) return;
        $filename = $_SERVER["PATH_INFO"];

        // If path info starts with /scripts/ or /script-resources/ then
        // send the resource back to the client
        if (strpos($filename, "/scripts/") === 0 ||
                strpos($filename, "/script-resources/") === 0 ||
                strpos($filename, "/script-source-bundles/") === 0 ||
                strpos($filename, "/script-bundles/") === 0 ||
                strpos($filename, "/script-sources/") === 0 ||
                strpos($filename, "/scripts/") === 0)
            $this->sendResource($filename);
    }

    /**
     * Returns the URL to the specified resource.
     *
     * @param string $resource
     *            The resource name. Defaults to empty string.
     * @param boolean $absolute
     *            If returned URL must be absolute. Defaults to false.
     */
    function getResourceUrl($resource = "", $absolute = false)
    {
        if ($absolute)
            $url = "http://" . $_SERVER["SERVER_NAME"] . ":" .
                $_SERVER["SERVER_PORT"] . $_SERVER["PHP_SELF"];
        else
            $url = basename($_SERVER["PHP_SELF"]);
        echo $url . "/script-resources" . $resource;
    }
}

$resolver = Resolver::getInstance();
$resolver->processRequest();

?>