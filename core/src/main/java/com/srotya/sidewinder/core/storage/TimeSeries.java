/**
 * Copyright 2017 Ambud Sharma
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.srotya.sidewinder.core.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.srotya.sidewinder.core.filters.Tag;
import com.srotya.sidewinder.core.predicates.BetweenPredicate;
import com.srotya.sidewinder.core.predicates.Predicate;
import com.srotya.sidewinder.core.storage.compression.CompressionFactory;
import com.srotya.sidewinder.core.storage.compression.Reader;
import com.srotya.sidewinder.core.storage.compression.RollOverException;
import com.srotya.sidewinder.core.storage.compression.Writer;
import com.srotya.sidewinder.core.utils.TimeUtils;

/**
 * Persistent version of {@link TimeSeries}. Persistence is provided via keeping
 * series buckets in a text file whenever a new series is added. Series buckets
 * are added not that frequently therefore using a text format is fairly
 * reasonable. This system is prone to instant million file appends at the mark
 * of series bucket time boundary i.e. all series will have bucket changes at
 * the same time however depending on the frequency of writes, this may not be
 * an issue.
 * 
 * @author ambud
 */
public class TimeSeries {

	private static final int COMPACTION_THRESHOLD = 2;
	private static final int START_OFFSET = 2;

	private static final Logger logger = Logger.getLogger(TimeSeries.class.getName());
	private SortedMap<String, List<Writer>> bucketMap;
	private boolean fp;
	private AtomicInteger retentionBuckets;
	private String seriesId;
	private Class<Writer> compressionClass;
	private Class<Writer> compactionClass;
	private Map<String, String> conf;
	private Measurement measurement;
	private int timeBucketSize;
	// used for unit tests only
	private int bucketCount;
	private Map<String, List<Writer>> compactionCandidateSet;
	private boolean compactionEnabled;
	private double compactionRatio;

	/**
	 * @param measurement
	 * @param compressionCodec
	 * @param compactionCodec
	 * @param seriesId
	 * @param timeBucketSize
	 * @param metadata
	 * @param fp
	 * @param conf
	 * @throws IOException
	 */
	public TimeSeries(Measurement measurement, String compressionCodec, String compactionCodec, String seriesId,
			int timeBucketSize, DBMetadata metadata, boolean fp, Map<String, String> conf) throws IOException {
		this.compressionClass = CompressionFactory.getClassByName(compressionCodec);
		this.compactionClass = CompressionFactory.getClassByName(compactionCodec);
		this.measurement = measurement;
		this.seriesId = seriesId;
		this.timeBucketSize = timeBucketSize;
		this.conf = new HashMap<>(conf);
		retentionBuckets = new AtomicInteger(0);
		setRetentionHours(metadata.getRetentionHours());
		this.fp = fp;
		bucketMap = measurement.createNewBucketMap(seriesId);
		this.compactionCandidateSet = new HashMap<>();
		compactionEnabled = Boolean.parseBoolean(
				conf.getOrDefault(StorageEngine.COMPACTION_ENABLED, StorageEngine.DEFAULT_COMPACTION_ENABLED));
		compactionRatio = Double
				.parseDouble(conf.getOrDefault(StorageEngine.COMPACTION_RATIO, StorageEngine.DEFAULT_COMPACTION_RATIO));
	}

	public Writer getOrCreateSeriesBucket(TimeUnit unit, long timestamp) throws IOException {
		String tsBucket = getTimeBucket(unit, timestamp, timeBucketSize);
		List<Writer> list = bucketMap.get(tsBucket);
		if (list == null) {
			// potential opportunity to load bucket information from some other
			// non-memory
			// location
			synchronized (bucketMap) {
				if ((list = bucketMap.get(tsBucket)) == null) {
					list = Collections.synchronizedList(new ArrayList<>());
					createNewWriter(timestamp, tsBucket, list);
					bucketMap.put(tsBucket, list);
					logger.fine(() -> "Creating new time series bucket:" + seriesId + ",measurement:"
							+ measurement.getMeasurementName());
				}
			}
		}

		synchronized (list) {
			Writer ans = list.get(list.size() - 1);
			if (ans.isFull()) {
				if ((ans = list.get(list.size() - 1)).isFull()) {
					final Writer ansTmp = ans;
					logger.fine(() -> "Requesting new writer for:" + seriesId + ",measurement:"
							+ measurement.getMeasurementName() + " bucketcount:" + bucketCount + " pos:"
							+ ansTmp.getPosition());
					ans = createNewWriter(timestamp, tsBucket, list);
					// if there are more than 2 buffers in the list then it is a
					// candidate for
					// compaction else not because 2 or less buffers means there
					// is at least 1
					// writable buffer which can't be compacted
					// #COMPACTHRESHOLD
					if (compactionEnabled && list.size() > COMPACTION_THRESHOLD) {//
						// add older bucket to compaction queue
						final List<Writer> listTmp = list;
						logger.fine(() -> "Adding bucket to compaction set:" + listTmp.size());
						compactionCandidateSet.put(tsBucket, list);
					}
				}
			}
			return ans;
			// Old code used for thread safety checks
			// try {
			// int idx = list.indexOf(ans);
			// if (idx != (list.size() - 1)) {
			// System.out.println("\n\nThread safety error\t" + idx + "\t" +
			// list.size() +
			// "\n\n");
			// }
			// } catch (Exception e) {
			// logger.log(Level.SEVERE, "Create new:" + "\tList:" + list +
			// "\tbucket:" +
			// tsBucket + "\t" + bucketMap,
			// e);
			// throw e;
			// }
		}
	}

	public static String getTimeBucket(TimeUnit unit, long timestamp, int timeBucketSize) {
		int bucket = TimeUtils.getTimeBucket(unit, timestamp, timeBucketSize);
		String tsBucket = Integer.toHexString(bucket);
		return tsBucket;
	}

	private Writer createNewWriter(long timestamp, String tsBucket, List<Writer> list) throws IOException {
		BufferObject bufPair = measurement.getMalloc().createNewBuffer(seriesId, tsBucket);
		bufPair.getBuf().put((byte) CompressionFactory.getIdByClass(compressionClass));
		bufPair.getBuf().put((byte) list.size());
		Writer writer;
		writer = getWriterInstance(compressionClass);
		writer.setBufferId(bufPair.getBufferId());
		// first byte is used to store compression codec type
		writer.configure(conf, bufPair.getBuf(), true, START_OFFSET, true);
		writer.setHeaderTimestamp(timestamp);
		list.add(writer);
		bucketCount++;
		logger.fine(() -> "Created new writer for:" + tsBucket + " timstamp:" + timestamp + " buckectInfo:"
				+ bufPair.getBufferId());
		return writer;
	}

	private Writer getWriterInstance(Class<Writer> compressionClass) {
		try {
			Writer writer = compressionClass.newInstance();
			return writer;
		} catch (InstantiationException | IllegalAccessException e) {
			// should never happen unless the constructors are hidden
			throw new RuntimeException(e);
		}
	}

	/**
	 * Function to check and recover existing bucket map, if one exists.
	 * 
	 * @param bufferEntries
	 * @throws IOException
	 */
	public void loadBucketMap(List<Entry<String, BufferObject>> bufferEntries) throws IOException {
		Map<String, String> cacheConf = new HashMap<>(conf);
		logger.fine(() -> "Scanning buffer for:" + seriesId);
		for (Entry<String, BufferObject> entry : bufferEntries) {
			ByteBuffer duplicate = entry.getValue().getBuf();
			duplicate.rewind();
			// String series = getStringFromBuffer(duplicate);
			// if (!series.equalsIgnoreCase(seriesId)) {
			// continue;
			// }
			String tsBucket = entry.getKey();
			List<Writer> list = bucketMap.get(tsBucket);
			if (list == null) {
				list = Collections.synchronizedList(new ArrayList<>());
				bucketMap.put(tsBucket, list);
			}
			ByteBuffer slice = duplicate.slice();
			int codecId = (int) slice.get();
			// int listIndex = (int) slice.get();
			Class<Writer> classById = CompressionFactory.getClassById(codecId);
			Writer writer = getWriterInstance(classById);
			if (entry.getValue().getBufferId() == null) {
				throw new IOException("Buffer id can't be read:" + measurement.getDbName() + ":"
						+ measurement.getMeasurementName() + " series:" + getSeriesId());
			}
			logger.fine(() -> "Loading bucketmap:" + seriesId + "\t" + tsBucket + " bufferid:"
					+ entry.getValue().getBufferId());
			writer.setBufferId(entry.getValue().getBufferId());
			writer.configure(cacheConf, slice, false, START_OFFSET, true);
			list.add(writer);
			bucketCount++;
			logger.fine(() -> "Loaded bucketmap:" + seriesId + "\t" + tsBucket + " bufferid:"
					+ entry.getValue().getBufferId());
		}
		sortBucketMap();
	}

	private void sortBucketMap() throws IOException {
		for (Entry<String, List<Writer>> entry : bucketMap.entrySet()) {
			Collections.sort(entry.getValue(), new Comparator<Writer>() {

				@Override
				public int compare(Writer o1, Writer o2) {
					return Integer.compare((int) o1.getRawBytes().get(1), (int) o2.getRawBytes().get(1));
				}
			});
			for (int i = 0; i < entry.getValue().size() - 1; i++) {
				Writer writer = entry.getValue().get(i);
				writer.makeReadOnly();
			}
			// #COMPACTHRESHOLD
			if (entry.getValue().size() > COMPACTION_THRESHOLD) {
				compactionCandidateSet.put(entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Extract {@link DataPoint}s for the supplied time range and value predicate.
	 * 
	 * Each {@link DataPoint} has the appendFieldValue and appendTags set in it.
	 * 
	 * @param appendFieldValueName
	 *            fieldname to append to each datapoint
	 * @param startTime
	 *            time range beginning
	 * @param endTime
	 *            time range end
	 * @param valuePredicate
	 *            pushed down filter for values
	 * @return list of datapoints
	 * @throws IOException
	 */
	public List<DataPoint> queryDataPoints(String appendFieldValueName, long startTime, long endTime,
			Predicate valuePredicate) throws IOException {
		if (startTime > endTime) {
			// swap start and end times if they are off
			startTime = startTime ^ endTime;
			endTime = endTime ^ startTime;
			startTime = startTime ^ endTime;
		}
		BetweenPredicate timeRangePredicate = new BetweenPredicate(startTime, endTime);
		logger.fine(getSeriesId() + " " + bucketMap.size() + " " + bucketCount + " " + startTime + "  " + endTime + " "
				+ valuePredicate + " " + timeRangePredicate + " diff:" + (endTime - startTime));
		SortedMap<String, List<Writer>> series = correctTimeRangeScan(startTime, endTime);
		List<Reader> readers = new ArrayList<>();
		for (List<Writer> writers : series.values()) {
			for (Writer writer : writers) {
				readers.add(getReader(writer, timeRangePredicate, valuePredicate));
			}
		}
		List<DataPoint> points = new ArrayList<>();
		for (Reader reader : readers) {
			readerToDataPoints(points, reader);
		}
		return points;
	}

	private SortedMap<String, List<Writer>> correctTimeRangeScan(long startTime, long endTime) {
		int tsStartBucket = TimeUtils.getTimeBucket(TimeUnit.MILLISECONDS, startTime, timeBucketSize) - timeBucketSize;
		String startTsBucket = Integer.toHexString(tsStartBucket);
		int tsEndBucket = TimeUtils.getTimeBucket(TimeUnit.MILLISECONDS, endTime, timeBucketSize);
		String endTsBucket = Integer.toHexString(tsEndBucket);
		if (startTsBucket.compareTo(bucketMap.firstKey()) < 0) {
			startTsBucket = bucketMap.firstKey();
			logger.finest(() -> "Corrected query startKey to:" + bucketMap.firstKey());
		}
		SortedMap<String, List<Writer>> series = null;
		if (bucketMap.size() <= 1) {
			series = bucketMap;
		} else {
			if (endTsBucket.compareTo(bucketMap.lastKey()) > 0) {
				series = bucketMap.tailMap(startTsBucket);
				logger.finest(() -> "Endkey exceeds last key, using tailmap instead");
			} else {
				endTsBucket = endTsBucket + Character.MAX_VALUE;
				series = bucketMap.subMap(startTsBucket, endTsBucket);
			}
		}
		logger.fine("Series select size:" + series.size());
		return series;
	}

	public List<long[]> queryPoints(String appendFieldValueName, List<String> appendTags, long startTime, long endTime,
			Predicate valuePredicate) throws IOException {
		if (startTime > endTime) {
			// swap start and end times if they are off
			startTime = startTime ^ endTime;
			endTime = endTime ^ startTime;
			startTime = startTime ^ endTime;
		}
		BetweenPredicate timeRangePredicate = new BetweenPredicate(startTime, endTime);
		SortedMap<String, List<Writer>> series = correctTimeRangeScan(startTime, endTime);
		List<Reader> readers = new ArrayList<>();
		for (List<Writer> writers : series.values()) {
			for (Writer writer : writers) {
				readers.add(getReader(writer, timeRangePredicate, valuePredicate));
			}
		}
		List<long[]> points = new ArrayList<>();
		for (Reader reader : readers) {
			readerToPoints(points, reader);
		}
		return points;
	}

	/**
	 * Get {@link Reader} with time and value filter predicates pushed-down to it.
	 * 
	 * @param timePredicate
	 * @param valuePredicate
	 * @return point in time instance of reader
	 * @throws IOException
	 */
	public static Reader getReader(Writer writer, Predicate timePredicate, Predicate valuePredicate)
			throws IOException {
		Reader reader = writer.getReader();
		reader.setTimePredicate(timePredicate);
		reader.setValuePredicate(valuePredicate);
		return reader;
	}

	/**
	 * Extract list of readers for the supplied time range and value predicate.
	 * 
	 * Each {@link DataPoint} has the appendFieldValue and appendTags set in it.
	 * 
	 * @param appendFieldValueName
	 *            fieldname to append to each datapoint
	 * @param appendTags
	 *            tags to append to each datapoint
	 * @param startTime
	 *            time range beginning
	 * @param endTime
	 *            time range end
	 * @param valuePredicate
	 *            pushed down filter for values
	 * @return list of readers
	 * @throws IOException
	 */
	public List<Reader> queryReader(String appendFieldValueName, List<Tag> appendTags, long startTime, long endTime,
			Predicate valuePredicate) throws IOException {
		if (startTime > endTime) {
			// swap start and end times if they are off
			startTime = startTime ^ endTime;
			endTime = endTime ^ startTime;
			startTime = startTime ^ endTime;
		}
		List<Reader> readers = new ArrayList<>();
		BetweenPredicate timeRangePredicate = new BetweenPredicate(startTime, endTime);
		SortedMap<String, List<Writer>> series = correctTimeRangeScan(startTime, endTime);
		for (List<Writer> writers : series.values()) {
			for (Writer writer : writers) {
				readers.add(getReader(writer, timeRangePredicate, valuePredicate));
			}
		}
		return readers;
	}

	/**
	 * Add data point with floating point value
	 * 
	 * @param unit
	 *            of time for the supplied timestamp
	 * @param timestamp
	 *            of this data point
	 * @param value
	 *            of this data point
	 * @throws IOException
	 */
	public void addDataPoint(TimeUnit unit, long timestamp, double value) throws IOException {
		Writer writer = getOrCreateSeriesBucket(unit, timestamp);
		try {
			writer.addValue(timestamp, value);
		} catch (RollOverException e) {
			addDataPoint(unit, timestamp, value);
		} catch (NullPointerException e) {
			logger.log(Level.SEVERE, "\n\nNPE occurred for add datapoint operation\n\n", e);
		}
	}

	/**
	 * Add data point with non floating point value
	 * 
	 * @param unit
	 *            of time for the supplied timestamp
	 * @param timestamp
	 *            of this data point
	 * @param value
	 *            of this data point
	 * @throws IOException
	 */
	public void addDataPoint(TimeUnit unit, long timestamp, long value) throws IOException {
		Writer timeseriesBucket = getOrCreateSeriesBucket(unit, timestamp);
		try {
			timeseriesBucket.addValue(timestamp, value);
		} catch (RollOverException e) {
			addDataPoint(unit, timestamp, value);
		} catch (NullPointerException e) {
			logger.log(Level.SEVERE, "\n\nNPE occurred for add datapoint operation\n\n", e);
		}
	}

	public void addDataPoints(TimeUnit unit, List<DataPoint> dps) throws IOException {
		Map<Writer, List<DataPoint>> dpMap = new HashMap<>();
		for (DataPoint dp : dps) {
			Writer writer = getOrCreateSeriesBucket(unit, dp.getTimestamp());
			List<DataPoint> dpx;
			if (!dpMap.containsKey(writer)) {
				dpMap.put(writer, dpx = new ArrayList<>());
			} else {
				dpx = dpMap.get(writer);
			}
			dpx.add(dp);
		}
		for (Entry<Writer, List<DataPoint>> entry : dpMap.entrySet()) {
			entry.getKey().write(entry.getValue());
		}
	}

	/**
	 * Converts timeseries to a list of datapoints appended to the supplied list
	 * object. Datapoints are filtered by the supplied predicates before they are
	 * returned. These predicates are pushed down to the reader for efficiency and
	 * performance as it prevents unnecessary object creation.
	 * 
	 * @param appendFieldValueName
	 * @param appendTags
	 * 
	 * @param points
	 *            list data points are appended to
	 * @param writer
	 *            to extract the data points from
	 * @param timePredicate
	 *            time range filter
	 * @param valuePredicate
	 *            value filter
	 * @return the points argument
	 * @throws IOException
	 */
	public static List<DataPoint> seriesToDataPoints(String appendFieldValueName, List<String> appendTags,
			List<DataPoint> points, Writer writer, Predicate timePredicate, Predicate valuePredicate, boolean isFp)
			throws IOException {
		Reader reader = getReader(writer, timePredicate, valuePredicate);
		DataPoint point = null;
		while (true) {
			try {
				point = reader.readPair();
				if (point != null) {
					points.add(point);
				}
			} catch (IOException e) {
				if (e instanceof RejectException) {
				} else {
					e.printStackTrace();
				}
				break;
			}
		}
		return points;
	}

	public static void readerToDataPoints(List<DataPoint> points, Reader reader) throws IOException {
		DataPoint point = null;
		while (true) {
			try {
				point = reader.readPair();
				if (point != null) {
					points.add(point);
				}
			} catch (IOException e) {
				if (e instanceof RejectException) {
				} else {
					e.printStackTrace();
				}
				break;
			}
		}
		if (reader.getCounter() != reader.getPairCount() || points.size() < reader.getCounter()) {
			logger.finest(() -> "SDP:" + points.size() + "/" + reader.getCounter() + "/" + reader.getPairCount());
		}
	}

	public static void readerToPoints(List<long[]> points, Reader reader) throws IOException {
		long[] point = null;
		while (true) {
			try {
				point = reader.read();
				if (point != null) {
					points.add(point);
				}
			} catch (IOException e) {
				if (e instanceof RejectException) {
				} else {
					logger.log(Level.SEVERE, "Non rejectexception while reading datapoints", e);
				}
				break;
			}
		}
		if (reader.getCounter() != reader.getPairCount() || points.size() < reader.getCounter()) {
			logger.finest(() -> "SDP:" + points.size() + "/" + reader.getCounter() + "/" + reader.getPairCount());
		}
	}

	/**
	 * Cleans stale series
	 * 
	 * @throws IOException
	 */
	public List<Writer> collectGarbage() throws IOException {
		List<Writer> gcedBuckets = new ArrayList<>();
		logger.finer("Retention buckets:" + retentionBuckets.get());
		while (bucketMap.size() > retentionBuckets.get()) {
			int oldSize = bucketMap.size();
			String key = bucketMap.firstKey();
			List<Writer> buckets = bucketMap.remove(key);
			for (Writer bucket : buckets) {
				// bucket.close();
				gcedBuckets.add(bucket);
				logger.log(Level.FINEST,
						"GC," + measurement.getMeasurementName() + ":" + seriesId + " removing bucket:" + key
								+ ": as it passed retention period of:" + retentionBuckets.get() + ":old size:"
								+ oldSize + ":newsize:" + bucketMap.size() + ":");
			}
		}
		if (gcedBuckets.size() > 0) {
			logger.fine(() -> "GC," + measurement.getMeasurementName() + " buckets:" + gcedBuckets.size()
					+ " retention size:" + retentionBuckets);
		}
		return gcedBuckets;
	}

	/**
	 * Update retention hours for this TimeSeries
	 * 
	 * @param retentionHours
	 */
	public void setRetentionHours(int retentionHours) {
		int val = (int) (((long) retentionHours * 3600) / timeBucketSize);
		if (val < 1) {
			logger.fine("Incorrect bucket(" + timeBucketSize + ") or retention(" + retentionHours
					+ ") configuration; correcting to 1 bucket for measurement:" + measurement.getMeasurementName());
			val = 1;
		}
		this.retentionBuckets.set(val);
	}

	/**
	 * @return number of buckets to retain for this {@link TimeSeries}
	 */
	public int getRetentionBuckets() {
		return retentionBuckets.get();
	}

	/**
	 * @return the bucketMap
	 */
	public SortedMap<String, Writer> getBucketMap() {
		SortedMap<String, Writer> map = new TreeMap<>();
		for (Entry<String, List<Writer>> entry : bucketMap.entrySet()) {
			List<Writer> value = entry.getValue();
			for (int i = 0; i < value.size(); i++) {
				Writer bucketEntry = value.get(i);
				map.put(entry.getKey() + i, bucketEntry);
			}
		}
		return map;
	}

	public SortedMap<String, List<Writer>> getBucketRawMap() {
		return bucketMap;
	}

	/**
	 * @return the seriesId
	 */
	public String getSeriesId() {
		return seriesId;
	}

	/**
	 * @param seriesId
	 *            the seriesId to set
	 */
	public void setSeriesId(String seriesId) {
		this.seriesId = seriesId;
	}

	/**
	 * @return the fp
	 */
	public boolean isFp() {
		return fp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "TimeSeries [bucketMap=" + bucketMap + ", fp=" + fp + ", retentionBuckets=" + retentionBuckets
				+ ", logger=" + logger + ", seriesId=" + seriesId + ", timeBucketSize=" + timeBucketSize + "]";
	}

	public void close() throws IOException {
		// TODO close series
	}

	public int getTimeBucketSize() {
		return timeBucketSize;
	}

	/**
	 * Compacts old Writers into one for every single time bucket, this insures the
	 * buffers are compacted as well as provides an opportunity to use a higher
	 * compression rate algorithm for the bucket. All Writers but the last are
	 * read-only therefore performing operations on them does not impact.
	 * 
	 * @param functions
	 * @return returns null if nothing to compact or empty list if all compaction
	 *         attempts fail
	 * @throws IOException
	 */
	@SafeVarargs
	public final List<Writer> compact(Consumer<List<Writer>>... functions) throws IOException {
		// this loop only executes if there are any candidate buffers in the set
		// buckets should be moved out of the compaction set once they are
		// compacted
		// size check is to avoid unnecessary calls and exit fast
		if (compactionCandidateSet.isEmpty()) {
			return null;
		}
		List<Writer> compactedWriter = new ArrayList<>();
		Iterator<Entry<String, List<Writer>>> iterator = compactionCandidateSet.entrySet().iterator();
		int id = CompressionFactory.getIdByClass(compactionClass);
		while (iterator.hasNext()) {
			// entry.getKey() gives tsBucket string
			Entry<String, List<Writer>> entry = iterator.next();
			// remove this entry from compaction set
			iterator.remove();
			List<Writer> list = entry.getValue();
			int listSize = list.size() - 1;
			int pointCount = list.subList(0, listSize).stream().mapToInt(s -> s.getCount()).sum();
			int total = list.subList(0, listSize).stream().mapToInt(s -> s.getPosition()).sum();
			if (total == 0) {
				logger.warning("Ignoring bucket for compaction, not enough bytes. THIS BUG SHOULD BE INVESTIGATED");
				continue;
			}
			Writer writer = getWriterInstance(compactionClass);
			int compactedPoints = 0;
			double bufSize = total * compactionRatio;
			logger.finer("Allocating buffer:" + total + " Vs. " + pointCount * 16 + " max compacted buffer:" + bufSize);
			logger.finer("Getting sublist from:" + 0 + " to:" + (list.size() - 1));
			ByteBuffer buf = ByteBuffer.allocate((int) bufSize);
			buf.put((byte) id);
			// since this buffer will be the first one
			buf.put(1, (byte) 0);
			writer.configure(conf, buf, true, START_OFFSET, false);
			Writer input = list.get(0);
			// read the header timestamp
			long timestamp = input.getHeaderTimestamp();
			writer.setHeaderTimestamp(timestamp);
			// read all but the last writer and insert into new temp writer
			try {
				for (int i = 0; i < list.size() - 1; i++) {
					input = list.get(i);
					Reader reader = input.getReader();
					for (int k = 0; k < reader.getPairCount(); k++) {
						long[] pair = reader.read();
						writer.addValue(pair[0], pair[1]);
						compactedPoints++;
					}
				}
				writer.makeReadOnly();
			} catch (RollOverException e) {
				logger.warning("Buffer filled up; bad compression ratio; not compacting");
				continue;
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Compaction failed due to unknown exception", e);
			}
			// get the raw compressed bytes
			ByteBuffer rawBytes = writer.getRawBytes();
			// limit how much data needs to be read from the buffer
			rawBytes.limit(rawBytes.position());
			// convert buffer length request to size of 2
			int size = rawBytes.limit() + 1;
			if (size % 2 != 0) {
				size++;
			}
			rawBytes.rewind();
			// create buffer in measurement
			BufferObject newBuf = measurement.getMalloc().createNewBuffer(seriesId, entry.getKey(), size);
			logger.fine("Compacted buffer size:" + size + " vs " + total);
			String bufferId = newBuf.getBufferId();
			buf = newBuf.getBuf();
			writer = getWriterInstance(compactionClass);
			buf.put(rawBytes);
			writer.setBufferId(bufferId);
			writer.configure(conf, buf, false, START_OFFSET, false);
			writer.makeReadOnly();
			synchronized (list) {
				if (functions != null) {
					for (Consumer<List<Writer>> function : functions) {
						function.accept(list);
					}
				}
				size = listSize - 1;
				logger.finest("Compaction debug size differences size:" + size + " listSize:" + listSize + " curr:"
						+ list.size());
				for (int i = size; i >= 0; i--) {
					compactedWriter.add(list.remove(i));
				}
				list.add(0, writer);
				for (int i = 0; i < list.size(); i++) {
					list.get(i).getRawBytes().put(1, (byte) i);
				}
				// fix bucket count
				bucketCount -= size;
				logger.fine(
						"Total points:" + compactedPoints + ", original pair count:" + writer.getReader().getPairCount()
								+ " compression ratio:" + rawBytes.position() + " original:" + total);
			}
		}
		return compactedWriter;
	}

	/**
	 * FOR UNIT TESTING ONLY
	 * 
	 * @return
	 */
	public int getBucketCount() {
		return bucketCount;
	}

	/**
	 * FOR UNIT TESTING ONLY
	 * 
	 * @return
	 */
	public Collection<List<Writer>> getCompactionSet() {
		return compactionCandidateSet.values();
	}

	/**
	 * Method to help fix bucket writers directly
	 * 
	 * @param bucket
	 * @param bufList
	 * @throws IOException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	public void replaceFirstBuckets(String bucket, List<Entry<Long, byte[]>> bufList)
			throws IOException, InstantiationException, IllegalAccessException {
		boolean wasEmpty = false;
		List<Writer> list = bucketMap.get(bucket);
		if (list == null) {
			synchronized (bucketMap) {
				list = Collections.synchronizedList(new ArrayList<>());
				bucketMap.put(bucket, list);
				wasEmpty = true;
			}
		}
		synchronized (list) {
			// insert writers to list
			List<String> cleanupList = insertOrOverwriteWriters(bufList, wasEmpty, list, bucket);
			measurement.getMalloc().cleanupBufferIds(new HashSet<>(cleanupList));
		}
	}

	private List<String> insertOrOverwriteWriters(List<Entry<Long, byte[]>> bufList, boolean wasEmpty,
			List<Writer> list, String tsBucket) throws IOException, InstantiationException, IllegalAccessException {
		List<String> garbageCollectWriters = new ArrayList<>();
		if (!wasEmpty) {
			if (bufList.size() >= list.size()) {
				throw new IllegalArgumentException(
						"Buffer can't be replaced since local buffers are smaller than the replacing buffers");
			}
		}
		for (int i = 0; i < bufList.size(); i++) {
			if (!wasEmpty) {
				Writer removedWriter = list.remove(i);
				garbageCollectWriters.add(removedWriter.getBufferId());
			}
			Entry<Long, byte[]> bs = bufList.get(i);
			BufferObject bufPair = measurement.getMalloc().createNewBuffer(seriesId, tsBucket, bs.getValue().length);
			ByteBuffer buf = bufPair.getBuf();
			buf.put(bs.getValue());
			buf.rewind();
			Writer writer = CompressionFactory.getClassById(buf.get(0)).newInstance();
			writer.setBufferId(bufPair.getBufferId());
			writer.configure(conf, bufPair.getBuf(), false, START_OFFSET, true);
			writer.setHeaderTimestamp(bs.getKey());
			list.add(i, writer);
		}
		return garbageCollectWriters;
	}

}
