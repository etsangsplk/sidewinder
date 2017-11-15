# Configure
This page documents different configuration options for Sidewinder and also familiarize users with different configuration files Sidewinder needs to operate.

## Files
Sidewinder is configured using 2 configuration files:
1. Dropwizard YAML
2. Core Configuration File

## Dropwizard YAML
Sidewinder is a Dropwizard application, where Dropwizard provides the Jetty container where most of the read services including queries operate from. This file allows users to configure items like REST API and admin ports, service threads, logging etc. More details on Dropwizard configuration flags can be found here: http://www.dropwizard.io/1.0.5/docs/manual/configuration.html

In addition to the standard Dropwizard configuration, this file also points to the Core Configuration File using ```configPath``` property. Here are a few key configurations:

| Property         | Description       | Default          |
|------------------|-------------------|------------------|
|maxThreads|The maximum number of threads to use for requests.|1024|
|minThreads|The minimum number of threads to use for requests.|8|
|maxQueuedRequests|The maximum number of requests to queue before blocking the acceptors.|1024|
|idleThreadTimeout|The amount of time a worker thread can be idle before being stopped.|1 minute|
|shutdownGracePeriod|The maximum time to wait for Jetty, and all Managed instances, to cleanly shutdown before forcibly terminating them.|30 seconds|
|rootPath|The URL pattern relative to applicationContextPath from which the JAX-RS resources will be served.|/*|

##### Request Logging
The new request log uses the logback-access library for processing request logs, which allow to use an extended set of logging patterns. See the logback-access-pattern docs for the reference.

```
server:
  requestLog:
    appenders:
      - type: console
```

**Note: Please disable admin connectors for Dropwizard since they shouldn't be needed for any operation.**

## Core Configuration File
This file is the actual configuration file for almost everything in Sidewinder. It's a simple Java Properties file that follows ```key=value``` based configuration definition. Please refer to individual configuration items for details on the how to tune these parameters.
