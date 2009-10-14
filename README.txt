You have successfully created a plugin using the FishEye/Crucible plugin archetype. What to do now:

1. CUSTOMISE THE PLUGIN

- Generate project files for your IDE
  - If you use Eclipse, run 'mvn eclipse:eclipse' to generate an Eclipse project file.
  - If you use IDEA, run 'mvn idea:idea' to generate an IDEA project file.
- Edit pom.xml. Add information about your project, its developers and your organisation. Check the version of
  FishEye/Crucible in the dependencies section is correct.
- Edit the plugin descriptor, src/main/resources/atlassian-plugin.xml. Add or modify plugin modules in your project.
- Edit the plugin code in src/main/java/ or the unit tests in src/test/java/.

More documentation on Atlassian plugins is available here:

http://confluence.atlassian.com/display/DEVNET/How+to+Build+an+Atlassian+Plugin


2. BUILD THE PLUGIN

Building with your plugin with Maven is really easy:

- Run 'mvn compile' to compile the plugin.
- Run 'mvn test' to run the unit tests.
- Run 'mvn package' to produce the JAR.
- Run 'mvn fecru:run' to run your plugin in the latest version of FishEye/Crucible

Please remove this file before releasing your plugin.


** NOTE ON RESOURCE FILTERING **

The default pom has 'resource filtering' enabled, which means files in the src/main/resources directory will have
variables in the form ${var} replaced during the build process. For example, the default atlassian-plugin.xml includes
${project.artifactId}, which is replaced with the artifactId taken from the POM when building the plugin.

More information on resource filtering is available in the Maven documentation:

http://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html

