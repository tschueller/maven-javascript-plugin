maven-javascript-plugin
=======================


Description
-----------

This plugin (Maven 2 or 3) provides support to work with JavaScript projects.
It compiles scripts with Google's Closure Compiler, packages them into a JAR
file and also has dependency support so a JavaScript project can depend on
other JavaScript projects.

But be warned: If you are searching for a Maven plugin to simply compress some
JavaScript files with Google's Closure Compiler then you are wrong here.
This plugin enforces a specific usage which is not configurable to ensure that
all JavaScript projects managed by this plugin are compatible to each other.


Repositories
------------

This plugin and its dependencies are regularly deployed into my personal
[Maven repository][1]. Add this configuration to your POM to automatically
use this repo:

    <repositories> 
      <repository>
        <id>ailis-releases</id> 
        <name>Ailis Maven Releases</name> 
        <url>https://www.ailis.de/nexus/content/repositories/releases</url> 
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
      </repository>
      <repository>
        <id>ailis-snapshots</id>
        <name>Ailis Maven Snapshots</name>
        <url>https://www.ailis.de/nexus/content/repositories/snapshots</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
      </repository>
    </repositories>

  
Usage
-----

To use this plugin simply create a pom.xml which contains at least this
information:

    <project xmlns="http://maven.apache.org/POM/4.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM
      <modelVersion>4.0.0</modelVersion>
    
      <groupId>your.group.id</groupId>
      <artifactId>yourArtifactId</artifactId>
      <version>1.0.0-SNAPSHOT</version>

      <packaging>javascript</packaging>
    
      <build>
        <sourceDirectory>src/main/javascript</sourceDirectory>
        <outputDirectory>target/files</outputDirectory>
        <plugins>
          <plugin>
            <groupId>de.ailis.maven.plugins</groupId>
            <artifactId>maven-javascript-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <extensions>true</extensions>
          </plugin>
        </plugins>
      </build>
    </project>

As you can see you have to include the plugin with the *extensions* setting
set to *true* and you also have to set the *packaging* type to *javascript*.

Setting a custom source and output directory is not required but recommended
because it's quite nasty to have JavaScript files in a directory called
*java* and the compiler output in a directory called *classes*.


How it works
------------

Let's assume you have the following source files:

    src/main/javascript/
      foobar/
        foo.js
        bar.js
      foobar.js
    src/main/resources/
      images/
        image1.png
        image2.png
        
When you use the POM from above and compile this project then you end up with
a target directory content like this:

    target/files/
      script-sources/
        foobar/
          foo.js
          bar.js
        foobar.js
      scripts/
        foobar/
          foo.js
          bar.js
        foobar.js
      script-source-bundles/
        your.group.id.yourArtifactId.js
      script-bundles/
        your.group.id.yourArtifactId.js
      script-resources/
        images/
          image1.png
          image2.png
          
So what exactly has happened here? 

1. All script resources were copied to the *script-resources* directory. This
   copying is compatible to the standard Java resource copying so you can
   setup multiple resource directories, inclusion/exclusion patterns and you
   can also filter the resources (for expanding properties in resource files).
2. All script sources are copied to the *script-sources* directory. Filtering
   is enabled by default so properties are expanded (Can be disabled in the
   plugin configuration).
3. The copied script sources are parsed by the dependency manager to bring
   them into a working order (How the order is calculated is explained later).
4. The ordered script sources are written into the *script-source-bundles*
   directory. The bundle filename is the group id plus the artifact id. This
   can be changed in the plugin configuration. 
5. The script sources are compiled with Google's closure compiler. The
   source bundles of external dependencies are used as *externs* so the 
   Closure Compiler can validate the correct usage of external dependencies.
   When compilation succeeds then the compiled scripts are written to the
   *scripts* directory.
6. All compiled scripts are written (in the correct order) into a single
   file in the *script-bundles* directory. The filename is the same as
   the script source bundle.

When packaging the project all these folders are put into a JAR which then can
be deployed into some maven repository so other projects can depend on it.


Dependency management
---------------------

The plugin needs to know which JavaScript file depends on which other JavaScript
files so the files are bundled in the correct order. To achieve this the files
must use special annotations to describe this dependency tree.
Let's say the *foobar/foo.js* defines a function in the *foobar* namespace.
This namespace is defined by the *foobar.js* file. So the plugin must place
the content of the *foobar.js* file at the top of the created bundle. So the
*foobar/foo.js* file must tell the dependency manager that it requires the
file *foobar.js* before it can work. Here is the content of this file:

    /**
     * @require foobar.js
     */
     
     foobar.doSomething = function() {};
     
If a file just *uses* another file but does not require it to be loaded first
you can use the *@use* annotation. Example:

    /**
     * @require foobar.js
     * @use foobar/foo.js
     */
     
     foobar.doSomethingElse = function()
     {
         foobar.doSomething();
     };     

This dependency type is not really needed at compile time but it is retained
so it can be used at runtime. 

The plugin supports another annotation but this one is normally not used 
manually but instead it is automatically added to script bundles: The *@provide*
annotation. For each file which is included in the bundle one annotation is
added. This can be used at runtime to check which bundle must be loaded to
fulfill a requirement of an other script. The compiled bundle could look like
this for example:

    (All scripts in compiled form here)
    /**
     * @provide foobar.js
     * @provide foobar/foo.js
     * @provide foobar/bar.js
     * @require jquery.js
     * @use prototype.js
     */  
     
As you can see the bundle still contains *@require* and *@use* annotations
which were not resolved to local scripts. So if the file *foobar/bar.js*
requires *jquery* and uses *prototype* then these dependencies are written to
the bundle file so they can be read at runtime.


Runtime dependency management
-----------------------------

The section above mentioned runtime dependency management here and there and
you may wonder what this is. The Maven plugin has no support for this because
this is not the job of the plugin. Instead you could write a dependency
manager in PHP or Java or whatever which implements a system which can work
with the annotations in the javascript files and also can control what type
of file (single files, bundles, compiled form, source form) is used. Up to now
no such software exists.


TODOs
-----

* Add demo support. Current idea: Place some demo HTML files into */src/demo*.
  Plugin copies them into */target/demo* and parses and expands the script
  inclusions. All used scripts are also copied to the */target/demo* directory
  so you can simply call the HTML files in your browser.
* Add junit support. Idea: Plugin provides a JavaScript wrapper for JUnit
  (Using rhino) so Unit-Tests in */src/test/javascript* can be executed.
* Add jsdoc support. Idea: Use the new rhino-based JSDoc implementation to
  create API documentation.
* Implement runtime dependency manager for PHP and Java.
* Write m2e plugin so all this magic nicely integrates into Eclipse. 

[1]: https://www.ailis.de/nexus/content/repositories/ "Ailis Maven Repository"
