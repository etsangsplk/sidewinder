## Install
Sidewinder can be installed in one of several different ways depending on the environment your are deploying.

### Pre-requisite
Sidewinder requires Java 8 (Oracle or OpenJDK) which can be installed using:

1. sudo yum install java (Centos 7)

2. sudo apt-get install openjdk-8-jre (Debian)

3. Oracle JRE: http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html

### Centos 6/7
Latest Sidewinder RPMs can be downloaded from Maven central e.g. http://search.maven.org/remotecontent?filepath=com/srotya/sidewinder/sidewinder-core/0.x.x/sidewinder-core-0.x.x.rpm

To install simply run ```sudo rpm -ivf sidewinder-core-0.x.x.rpm```

To upgrade simply run ```sudo rpm -Uv sidewinder-core-0.x.x.rpm```

### Debian
Latest Sidewinder RPMs can be downloaded from Maven central e.g. http://search.maven.org/remotecontent?filepath=com/srotya/sidewinder/sidewinder-core/0.0.18/sidewinder-core-0.0.18.rpm

To install simply run ```sudo dpkg -i sidewinder-core-0.x.x.deb```

### Docker
Official docker image for Sidewinder can be located on Docker Hub: https://hub.docker.com/r/srotya/sidewinder/tags/

### Native Java
To deploy Sidewinder on any platform you can also simply run the Jar file using standard Java Jar syntax. Here are the steps to do so:

1. Make sure you have Java (JRE/JDK) 8 installed

2. Download the latest jar from Maven central https://repo1.maven.org/maven2/com/srotya/sidewinder/sidewinder-core/

3. To run ```java -jar sidewinder-core-x.x.x.jar com.srotya.sidewinder.core.SidewinderServer server```


**Note: You can supply the YAML configuration file using the command below:**
```
java -jar sidewinder-core-x.x.x.jar com.srotya.sidewinder.core.SidewinderServer server <YAML config file path>
```

## Config
When using package installation, configuration for Sidewinder is located in the /etc/sidewinder folder with the config.yaml file and sidewinder.properties file with necessary configuration files. Here's the default configuration file:

#### YAML
```
server:
  gzip:
    enabled: true
    bufferSize: 8KiB
  applicationConnectors:
  - type: http
    port: 8080
  adminConnectors:
  - type: http
    port: 9001
  requestLog:
    appenders: []
configPath: /tmp/sidewinder.properties
```

The **sidewinder.properties** file can be just an empty file with no configuration settings. More details about configuration can be found in the [Config Section](/deployment/config)

## Run
The latest Sidewinder packages (RPM & DEB) also install a Linux service for it which can be run using the

```sudo service sidewinder start``` and logs to ```/var/log/sidewinder.log```
