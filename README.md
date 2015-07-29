# asmonitor

This Java utility supports the monitoring of [TIBCO ActiveSpaces](https://docs.tibco.com/products/tibco-activespaces-enterprise-edition-2-1-5) (AS) data grid.  It implemented an AS browser on the space '$space_stats', and writes runtime stats of all AS agents to a file at a pre-configured interval.

## Dependencies
This utility depends on the client library of TIBCO ActiveSpaces.
 
#### Maven

This is a Maven project, and so if Maven has not been installed on your system, you'll need to install Maven and Git as described in the [beunit](https://github.com/yxuco/beunit) project.
    
#### Clone this project from GitHub

In the root folder of your workspace, clone the project using command

    git clone https://github.com/yxuco/asmonitor.git

It should download the source code to the folder `asmonitor` in your workspace. 

#### Install TIBCO ActiveSpaces jar into local Maven repository

The following jar from TIBCO ActiveSpaces installation are used by this utility.

 - $AS_HOME/lib/as-common.jar
 
It is not available in Maven Central, so, you need to install it into your local Maven repository using the following command:

    mvn install:install-file -Dfile=$AS_HOME/lib/as-common.jar -DgroupId=com.tibco.as \
    -DartifactId=as-common -Dversion=2.1.5 -Dpackaging=jar

## Build the utility

In your workspace,

    cd asmonitor
    mvn clean package

The Maven build should be successful.  This step builds `asmonitor-2.1.jar` in the folder `$WORKSPACE/asmonitor/target/`.  In the same folder, you can find a sample configuration and script illustrating how to use this utility:
    asmonitor.sh - it shows how to start the utility (you can edit it to match your working environment.)
    config.properties - illustrates properties that can be edited to monitor multiple AS metaspaces.
    
The configurable properties are explained in the sample file.  Multi-value properties can be specified using a a unique suffix.  For example, if you to not want to print out stats for space_name that matches 2 string patterns, you can specify 2 exclusion patterns using 2 properties as
    exclude.space.1 pattern1
    exclude.space.2 pattern2
    
Similarly, you may specify multiple AS metaspace names and discovery URLs, and so all of them are monitored.  Note that each metaspace need to be specified only once in the file.  The monitor will print out stats of all AS agents in the specified metaspace. 

## Development using Eclipse
 
You may also edit and build the utility using either a standalone Eclipse, or the TIBCO BusinessEvents Studio.

 - Launch the TIBCO BusinessEvents Studio, for example.
 - Pulldown **File** menu and select **Import...**
 - In the **Import** dialog, select **Existing Maven Projects**, then click **Next >** button.
 - In the **Import Maven Projects** dialog, browse for **Root Directory**, select and open the `asmonitor` folder under your workspace.
 - Confirm that `your-workspace/asmonitor` is populated as the **Root Directory**, then click the **Finish** button.
 - In **Package Explorer**, highlight the root folder of the imported project `asmonitor`, and pulldown **Window** menu to open the Java Perspective.

## The author

Yueming is a Sr. Architect working at [TIBCO](http://www.tibco.com/) Architecture Service Group.