HOW TO BUILD
============
In order to build it's required to have JDK 1.7+ and Maven 3+, to get
a build going it's needed to:

1) Go in the api directory and run
   "mvn clean install"
   to generate API from YANG module. 

2) Go in the root directory and run 
   "mvn clean install"
   to build the entire project.
   To skip test and style check, please add arguments "-Dcheckstyle.skip -DskipTests".

