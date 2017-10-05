# Integrations
Several official integrations are available for Sidewinder to allow both ingress and egress of time series metrics data
from the database.

Currently the following integrations are supported:

### Grafana
https://grafana.com/

Part of the main Sidewinder project is also a Grafana Data Source for Sidewinder which can be downloaded to enable Grafana to interact with Sidewinder. This plugin is published as a zip and can be downloaded from Maven central

### Graphite
[https://graphiteapp.org](http://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol)

Sidewinder can now accept data from clients in Graphite PlainText protocol and has a built-in TCP Graphite endpoint.

### Collectd
https://collectd.org/

Sidewinder collectd writer plugin allows metrics captured or aggregated by Collectd to be forwarded to Sidewinder. Just like the Collectd JMX plugin the Sidewinder plugin is also Java based. Please refer to the Plugin Project for more details.

### Telegraf
https://github.com/influxdata/telegraf

Telegraf is a collection agent by InfluxDB, due to Sidewinder's compatibility with Influx's HTTP wire protocol makes it possible for Telegraf to be used directly with Sidewinder.
