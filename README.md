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

This plugin is regularly deployed into my personal [Maven repository][1]. 
Add this configuration to your POM to automatically use this repo:

    <repositories> 
      <repository>
        <id>ailis-releases</id> 
        <name>Ailis Maven Releases</name> 
        <url>http://nexus.ailis.de/content/repositories/releases</url> 
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
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
            <version>1.0.3</version>
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
          
So what exactly happens here? 

1. All script resources are copied to the *script-resources* directory.
2. All script sources are copied to the *script-sources* directory.
3. The copied script sources are parsed by the dependency manager to bring
   them into a working order (How the order is calculated is explained later).
4. The ordered script sources were written into the *script-source-bundles*
   directory. The bundle filename is the group id plus the artifact id. 
5. The script sources are compiled with Google's closure compiler. The
   source bundles of external dependencies are used as *externs* so the 
   Closure Compiler can validate the correct usage of external dependencies.
   When compilation succeeds then the compiled scripts are written to the
   *scripts* directory.
6. All compiled scripts are written (in the correct order) into a single
   file in the *script-bundles* directory. The filename is the same as
   the script source bundle.

When packaging the project all these folders are put into a JAR which then can
be deployed into some Maven repository so other projects can depend on it.


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

    /**
     * @provide foobar.js
     * @provide foobar/foo.js
     * @provide foobar/bar.js
     * @require jquery.js
     * @use prototype.js
     */  
    (All scripts in compiled form here)
     
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


Some words about the Closure Compiler
-------------------------------------

This plugin uses Google's Closure Compiler to compile the projects. The
Closure Compiler supports dozens of configuration settings but up to now the
JavaScript Maven plugin uses a fixed configuration which can't be changed. This
may change in the future but there are some settings which will never be
configurable:

* Renaming is only done for function parameters and variables. All global stuff
  (Even when annotated as private) is never renamed. JavaScript has no concept
  for private symbols so renaming them can trigger strange problems when 
  switching from uncompiled scripts to compiled scripts. So the base rule of
  the plugin is: The real API signature of all files must be retained.
* Only optimizations which doesn't affect the API signature are performed.
* All checks are enabled and set to ERROR level. In the future I might add
  some plugin configuration settings to lower the level of some checks to
  WARNING but up to now the plugin expects code which is 100% type-safe and
  error free.
  
  
Eclipse and m2e
---------------

This plugin works well in Eclipse with m2e as long as you add these lines to
your pom.xml:

    <build>
      <pluginManagement>
        <plugins>
          <plugin>
            <groupId>org.eclipse.m2e</groupId>
            <artifactId>lifecycle-mapping</artifactId>
            <version>1.0.0</version>
            <configuration>
              <lifecycleMappingMetadata>
                <pluginExecutions>
                  <pluginExecution>
                    <pluginExecutionFilter>
                      <groupId>de.ailis.maven.plugins</groupId>
                      <artifactId>maven-javascript-plugin</artifactId>
                      <versionRange>[1.0.0,)</versionRange>
                      <goals>
                        <goal>resources</goal>
                        <goal>compile</goal>
                        <goal>demo</goal>
                      </goals>
                    </pluginExecutionFilter>
                    <action>
                      <execute />
                    </action>
                  </pluginExecution>
                </pluginExecutions>
              </lifecycleMappingMetadata>
            </configuration>
          </plugin>
        </plugins>
      </pluginManagement>
    </build>

These lines tells Eclipse that it should simply execute the *resources*,
*compile* and *demo* rules without looking for some specialized eclipse plugin.

If done correctly your JavaScript Maven project is compiled by Eclipse (which
simply executes Maven) and you even get error and warning markers and
workspace dependency resolution.

JavaScript files are compiled on-save. But the build is not incremental so
every time you save a JavaScript file the whole project is compiled. If you
have a large project this might get pretty slow. When you can't bear it any
longer you can disable compile-on-save by configuring the plugin like this:

    <plugin>
      <groupId>de.ailis.maven.plugins</groupId>
      <artifactId>maven-javascript-plugin</artifactId>
      <version>1.0.3</version>
      <extensions>true</extensions>
      <configuration>
        <incremental>false</incremental>
      </configuration>
    </plugin>

By setting *incremental* to *false* you tell the plugin to do nothing when
Eclipse requests an incremental build. You have to do a full build (By
cleaning the project) to compile the project then.

Demo support
------------

It is often useful to have some demo files in the project which are used
during development to test the JavaScript application or demonstrate some
features. There is some basic support for this in the plugin but it requires
a locally installed Apache web server with enabled PHP because resolving the
Maven dependencies needs some dynamic processing.

Let's simply start right away and create a file named */src/demo/index.php*
with the following content:

    <?php require("../../target/demo/resolver.php"); ?>
    <!DOCTYPE html>
    <html>
      <head>
        <?php $resolver->includeScript("foobar/bar.js"); ?>
        <script type="text/javascript">
        
        var bar = foobar.Bar();
        bar.doSomething();
        
        </script>
      </head>
      <body>
        ...
      </body>
    </html>
 
This script contains two magic things: 

The first line includes the dependency resolver. This script is automatically 
written to the */target/demo* directory by the plugin. By simply including this 
file your demo PHP script automatically becomes a proxy script to access all 
the files in the Maven dependencies. So when you have packaged jQuery in a 
compatible format and your project depends on it then your browser can 
automatically access jquery by calling *index.php/scripts/jquery.js*. The
dependency resolver automatically fetches the script from the JAR file which
is located somewhere in your local maven repository. If you are using 
Eclipse with the m2e plugin then the dependency resolver can also access the
files from other m2e projects as long as you haven't disabled workspace
resolution.

The second magic in the above PHP script is the inclusion of the 
*foobar/bar.js*. THis is done by using the *includeScript* method of the
dependency resolver. The resolver checks the *@require* and *@use*
annotations of the included script and automatically (and recursively)
includes all the other needed files. So you don't have to change your demo
script when you add some dependency in a used file.
  
To be able to call the demo script you have to setup an Apache webserver with
PHP support on your local machine. On Linux you can simply link your project
directory to the */var/www* directory and then open
*http://localhost/myproject/src/demo/* in your browser. If your projects are
private you might consider configuring Apache to only listen on localhost or
setup some firewall rules.  


TODOs
-----

* Add junit support. Idea: Plugin provides a JavaScript wrapper for JUnit
  (Using rhino) so Unit-Tests in */src/test/javascript* can be executed.
* Add jsdoc support. Idea: Use the new rhino-based JSDoc implementation to
  create API documentation.
* Implement runtime dependency manager for PHP and Java.
* Ignore warnings/errors in dependencies.
* Place require/use/provide annotations below the first comment of the
  output file so license information is always on top.
* Use real dependencies instead of hardcoded externs.

[1]: http://nexus.ailis.de/content/repositories/ "Ailis Maven Repository"
