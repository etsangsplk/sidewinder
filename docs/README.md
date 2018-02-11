Sidewinder is an in-memory time series database designed for speed and scalability. It's purpose is to provide storage, retrieval and analysis for time series data generated over the last few days. It can be used to power dashboards and real-time correlations for time series data at scale.

# Features

### Various wire protocols
Besides it's own binary protocol format, Sidewinder also supports InfluxDB wire protocol format for ingestion and can be used with InfluxData Telegraf and any other agents that speak InfluxDB wire protocol.

### REST API (In-development)
Sidewinder offers a standard REST API to query and perform database operations.

### Grafana Support

[Grafana](http://grafana.org/) is an industry defacto for dashboards and visualizations. Sidewinder has it's own Grafana datasource which once installed can be used to connect to Sidewinder for creating visualizations.

### SQL Support

Sidewinder uses Apache Calcite to offer standard SQL support with some additional Time Series functions to make it user friendly.

# Downloads

#### Releases

Releases can be access through the Sidewinder releases page: https://github.com/srotya/sidewinder/releases

#### Jar

Sidewinder Jars can be found on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Csidewinder)


#### Docker
Docker images are available ```docker pull srotya/sidewinder:latest```

# License

Sidewinder is licensed under Apache 2.0, Copyright 2017 Ambud Sharma and contains sources from other projects documented in the LICENSE file.
