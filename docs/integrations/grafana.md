# Grafana Datasource
Sidewinder comes with out-of-the-box Grafana integrations and has an Official Grafana Data Source.

## How to Install?
To install the Grafana Plugin, simply download the code and copy the dist folder to the Grafana plugins folder.

1. ```git clone https://github.com/srotya/sidewinder-grafana.git```
2. ```cd sidewinder-grafana```
3. ```cp -r ./dist /var/lib/grafana/plugins/sidewinder``` on Linux

## How to use?

#### Pre-requisites
Make sure you have [deployed](http://sidewinder.srotya.com/docs/#/deployment/) Sidewinder and have the REST URL available. By default Sidewinder runs on ```port 8080```

#### Create Datasource
To create a new Sidewinder Grafana datasource:

1. [open Grafana](http://localhost:3000)
2. Click Grafana Icon->[Data Sources](http://localhost:3000/datasources)
3. Click [Add data source](http://localhost:3000/datasources/new)
4. Give the data source a name
5. Select Type to Sidewinder
6. Provide the Url to http://**sidewinder-host**:**sidewinder-port**/**database-name**
7. Leave Access to proxy
8. [Optional] Configure Basic Auth if authentication is enabled on Sidewinder
9. Click Add

```
Note: If you don't have a database created, you can use the _internal database used to store DB's performance metrics
```

Here's how an example data source should look like:
![Data Source Create](https://github.com/srotya/sidewinder-grafana/raw/master/docs/ds-create.png)

#### Create a Query Visually
Sidewinder support visual query building using an editor. There is support for auto-complete for all non-numeric fields (Measurement, Value Field, Tags). Please refer to [Sidewinder data model](http://sidewinder.srotya.com/docs/#/designs/?id=storage-engines) to understand the significance of different concepts.

Here's how an example Visual Query should look like:
![Data Source Edit](https://github.com/srotya/sidewinder-grafana/raw/master/docs/ds-edit.png)

#### Create a Query using TSQL
You can also use the Time Series Query Language to create panels. Please note that because Grafana provides the time ranges, you can't provide start and end time in the TSQL string. Please refer to the [docs](http://sidewinder.srotya.com/docs/#/integrations/queries) for more details on TSQL.

Here's how an example TSQL Query should look like:
![Data Source Edit Raw](https://github.com/srotya/sidewinder-grafana/raw/master/docs/ds-edit-raw.png)

#### Create Templates
Because Sidewinder supports TSQL, [grafana templated dashboards](http://docs.grafana.org/reference/templating/) can be created. You can create auto-complete suggestions for variables:
1. For FIELD name suggestion simply type ```measurementname``` in the "metric name or tag query field"
2. For TAG suggestion simply type ```measurementname.*``` in the "metric name or tag query field"

Here's how an example Templated Dashboard should look like:
![Data Source Template Edit](https://github.com/srotya/sidewinder-grafana/raw/master/docs/ds-edit-template.png)

#### Correlations
Applying PPMCC Correlations to Grafana
