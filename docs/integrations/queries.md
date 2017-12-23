# Queries
This section documents how queries can be executed against Sidewinder to allows users to use APIs to pull data programmatically instead of Grafana or other visualization tools.

## APIs

## Time Series Query Language (TSQL) - [0.0.22 or above]
Sidewinder introduces a new simplified query language designed for querying time series data. The syntax is easy and to the point and uses symbols to express the concepts of query. Let's understand the detailed syntax of the language.

``
Note: TSQL is fairly basic and will add more complexity as it matures
``

TSQL follows the format of:

```
[start-timestamp]<[series filter]<[end-timestamp]=>[function definition]
```

Here's an example:
```
2000-12-10T10:10:10<cpu.value.host=2&vm=2<2020-12-10T10:10:10=>derivative,10,smean
```

In the above example one can see the two timestamps between them is the filter for selecting the metrics to be queried finally ```=>``` feeds the selected data to a function, followed by the arguments of the function.

**Function Chaining**
Multiple functions can be chained together to output of one function becomes the input of another.

Here's an example:

```
2000-12-10T10:10:10<memory.used<2020-12-10T10:10:10=>divide,1048576=>avg,10,smean
```

In this example, the values selected will first be divided by 1048576 and then an average with a time window of 10 seconds.

### Series Filter
Series filter is a **.** separated expression:

```
[measurement name].[value field name].[tag filter expression]
```

The tag filter expression is an infix notation boolean expression, with tag name and boolean expression ```&``` i.e. AND, ```|``` i.e. OR. This combined together creates the tag filter that is used to

In the example below, we are selection ```measurement=cpu```, ```field=value``` where series has ```host=2 and vm=2```

```
cpu.value.host=2&vm=2
```

### Function Definition

Types of functions:
1. Single Result Function (single)
2. One to One Transform Function (transform)
3. Constant Function
4. Windowed Function
  a. Window Aggregator Function 0 - time window, 1 - aggregator name|

**List of Supported Functions**

|       Alias  |   Name      |   Description    |        Type          |
|--------------|-------------|------------------|----------------------|
|average,mean,avg|Windowed Mean|Returns the average value for each window of time|windowed aggregate|
|stddev|Windowed StdDev|Returns the standard deviation value for each window of time|windowed aggregate|
|rms|Root Mean Square|Returns the RMS value for each window of time|windowed aggregate|
|log|Log Function|Returns the log (base e) of each value in the series|transform|
|log10|Log10 Function|Returns the log (base 10) of each value in the series|transform|
|ceil|Ceil Function|Returns the ceil of each value in the series|transform|
|first|Windowed First|Returns the first value for each window of time|windowed aggregate|
|sin|Sine Function|Returns the sine of each value in the series|transform|
|cos|Cosine Function|Returns the cosine of each value in the series|transform|
|tan|Tangent Function|Returns the tangent of each value in the series|transform|
|diff|Diff  Function|Takes the difference between the next and current value|windowed aggregate|
|square|Square Function|Returns the square(^2) of each value in the series|transform|
|sqrt|Sqrt Function|Returns the square root of each value in the series|transform|
|cbrt|Cbrt Function|Returns the cube root of each value in the series|transform|
|cube|Cube Function|Returns the cube(^3) of each value in the series|transform|
|integral|Integral Function|Returns the integral (sum) value for each window of time|windowed aggregate|
|derivative,dvdt|Derivative Function|Takes the derivative value for each window of values|windowed aggregate|
|last|Windowed Last|Returns the last value for each window of time|windowed aggregate|
|floor|Floor Function|Returns the floor of each value in the series|transform|
|min|Windowed Min|Returns the minimum value for each window of time|windowed aggregate|
|abs|Absolute Function|Returns the absolute value (non-negative) for each value in the series|transform|
|max|Windowed Max|Returns the max value for each window of time|windowed aggregate|
|neg|Negate Function|Returns the negative (sign inverted) of each value in the series|transform|
|divide,div|Divide Function|Divides each value in the series with a constant|transform|
|add|Add Function|Adds a constant to each value in the series|transform|
|sub|Subtract Function|Subtracts a constant from each value in the series|transform|
|mult|Multiply Function|Multiplies a constant to each value in the series|transform|
|ms-multiplication|Multiplication|Multiplies values of all series|multi-series|
|ms-substraction|Substraction|Adds values of all series|multi-series|
|ms-addition|Addition|Subtracts first series by the rest of the series|multi-series|
|ms-average|Average|Averages all series|multi-series|
|ms-division|Division|Divides first series by the rest of the series|multi-series|
|smin|Min Function|Returns the smallest value in the series|single|
|srms|Single RMS|Returns the Root Mean Squared value of the series|single|
|slast|Last Function|Returns the last value in the series|single|
|ssum|Sum Function|Returns the sum of all value in the series|single|
|smean|Mean Function|Returns the average value of the series|single|
|sstddev|StdDeviation Function|Returns the standard deviation value of the series|single|
|smax|Max Function|Returns the largest value in the series|single|
|sfirst|First Function|Returns the first value in the series|single|

## Structured Query Language (SQL)
