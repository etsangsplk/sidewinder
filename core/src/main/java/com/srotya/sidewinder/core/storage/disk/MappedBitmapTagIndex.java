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
package com.srotya.sidewinder.core.storage.disk;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;

import org.roaringbitmap.buffer.MutableRoaringBitmap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.srotya.sidewinder.core.filters.ComplexTagFilter;
import com.srotya.sidewinder.core.filters.ComplexTagFilter.ComplexFilterType;
import com.srotya.sidewinder.core.filters.SimpleTagFilter;
import com.srotya.sidewinder.core.filters.TagFilter;
import com.srotya.sidewinder.core.monitoring.MetricsRegistryService;
import com.srotya.sidewinder.core.storage.SeriesFieldMap;
import com.srotya.sidewinder.core.storage.TagIndex;

/**
 * Tag hash lookup table + Tag inverted index
 * 
 * @author ambud
 */
public class MappedBitmapTagIndex implements TagIndex {

	private static final Logger logger = Logger.getLogger(MappedBitmapTagIndex.class.getName());
	private static final int INCREMENT_SIZE = 1024 * 1024 * 1;
	private Map<String, SortedMap<String, MutableRoaringBitmap>> rowKeyIndex;
	private String indexPath;
	private File revIndex;
	private Counter metricIndexRow;
	private boolean enableMetrics;
	private RandomAccessFile revRaf;
	private MappedByteBuffer rev;
	private PersistentMeasurement measurement;

	public MappedBitmapTagIndex(String indexDir, String measurementName, PersistentMeasurement measurement)
			throws IOException {
		this.measurement = measurement;
		this.indexPath = indexDir + "/" + measurementName;
		rowKeyIndex = new ConcurrentHashMap<>();
		revIndex = new File(indexPath + ".rev");
		MetricsRegistryService instance = MetricsRegistryService.getInstance();
		if (instance != null) {
			MetricRegistry registry = instance.getInstance("requests");
			metricIndexRow = registry.counter("index-row");
			enableMetrics = true;
		}
		loadTagIndex();
	}

	protected void loadTagIndex() throws IOException {
		if (!revIndex.exists()) {
			revRaf = new RandomAccessFile(revIndex, "rwd");
			rev = revRaf.getChannel().map(MapMode.READ_WRITE, 0, INCREMENT_SIZE);
			rev.putInt(0);
			logger.fine("Tag index is missing; initializing new index");
		} else {
			revRaf = new RandomAccessFile(revIndex, "rwd");
			logger.info("Tag index is present; recovering:" + revIndex.getAbsolutePath());
			rev = revRaf.getChannel().map(MapMode.READ_WRITE, 0, revIndex.length());

			// load reverse lookup
			int offsetLimit = rev.getInt();
			while (rev.position() < offsetLimit) {
				int length = rev.getInt();
				byte[] b = new byte[length];
				rev.get(b);
				String r = new String(b);
				String[] split = r.split(" ");
				String tagKey = split[0];
				String tagValue = split[1];
				SortedMap<String, MutableRoaringBitmap> map = rowKeyIndex.get(tagKey);
				if (map == null) {
					map = new ConcurrentSkipListMap<>();
					rowKeyIndex.put(tagKey, map);
					logger.finest(() -> "Map for tagkey:" + tagKey + " not found, creating it");
				}
				MutableRoaringBitmap set = map.get(tagValue);
				if (set == null) {
					set = new MutableRoaringBitmap();
					map.put(tagValue, set);
					logger.finest(() -> "Map for tagValue(" + tagKey + "):" + tagValue + " not found, creating it");
				}
				String rowKeyIndex = split[2];
				set.add(Integer.parseInt(rowKeyIndex));
			}
			logger.fine(() -> "Tag index recovered" + revIndex.getAbsolutePath());
		}
	}

	@Override
	public Set<String> getTagKeys() {
		Set<String> set = new HashSet<>(rowKeyIndex.keySet());
		return set;
	}

	private void bitmapToRowKeys(Collection<String> rowKeys, MutableRoaringBitmap value) {
		logger.finest(() -> "Requesting conversion from bitmap to value");
		List<SeriesFieldMap> ref = measurement.getSeriesListAsList();
		for (Iterator<Integer> iterator = value.iterator(); iterator.hasNext();) {
			Integer idx = iterator.next();
			String seriesId = ref.get(idx).getSeriesId();
			rowKeys.add(seriesId);
			logger.finest(
					() -> "Adding idx:" + idx + " resolving to seriesId:" + seriesId + " for bitmap extrapolation");
		}
	}

	protected MutableRoaringBitmap evalFilterForTags(TagFilter filterTree) {
		logger.finer(() -> "Evaluating filter tree:" + measurement.getMeasurementName() + " " + filterTree);
		// either it's a simple tag filter or a complex tag filter
		if (filterTree instanceof SimpleTagFilter) {
			SimpleTagFilter simpleFilter = (SimpleTagFilter) filterTree;
			SortedMap<String, MutableRoaringBitmap> map = rowKeyIndex.get(simpleFilter.getTagKey());
			if (map == null) {
				return null;
			}
			return evalSimpleTagFilter(simpleFilter, map);
		} else {
			// if it's a complex tag filter then get individual units of return
			ComplexTagFilter complexFilter = (ComplexTagFilter) filterTree;
			List<TagFilter> filters = complexFilter.getFilters();
			ComplexFilterType type = complexFilter.getType();
			MutableRoaringBitmap map = new MutableRoaringBitmap();
			for (int i = 0; i < filters.size(); i++) {
				TagFilter tagFilter = filters.get(i);
				MutableRoaringBitmap r = evalFilterForTags(tagFilter);
				if (r == null) {
					// no match found from evaluation of this filter
					if (type == ComplexFilterType.AND) {
						// if filter condition is AND then short circuit terminate the evaluation
						return map = new MutableRoaringBitmap();
					} else {
						// if filter condition is OR then continue evaluation
						continue;
					}
				} else if (map.isEmpty()) {
					// if the global map is empty then initialize it
					map.or(r);
				}
				switch (type) {
				case AND:
					map.and(r);
					break;
				case OR:
					map.or(r);
					break;
				}
			}
			return map;
		}
	}

	public static void printBitMap(MutableRoaringBitmap r) {
		for (Integer integer : r) {
			System.out.println("Row:" + integer);
		}
	}

	private MutableRoaringBitmap evalSimpleTagFilter(SimpleTagFilter simpleFilter,
			SortedMap<String, MutableRoaringBitmap> map) {
		switch (simpleFilter.getFilterType()) {
		case EQUALS:
			return map.get(simpleFilter.getComparedValue());
		case GREATER_THAN:
			SortedMap<String, MutableRoaringBitmap> tailMap = map.tailMap(simpleFilter.getComparedValue());
			if (tailMap.isEmpty()) {
				return null;
			}
			Iterator<MutableRoaringBitmap> iterator = tailMap.values().iterator();
			// skip the first one since the condition is greater than
			iterator.next();
			return combineMaps(iterator);
		case LESS_THAN:
			SortedMap<String, MutableRoaringBitmap> headMap = map.headMap(simpleFilter.getComparedValue());
			if (headMap.isEmpty()) {
				return null;
			}
			return combineMaps(headMap.values().iterator());
		case GREATER_THAN_EQUALS:
			SortedMap<String, MutableRoaringBitmap> tailMap1 = map.tailMap(simpleFilter.getComparedValue());
			if (tailMap1.isEmpty()) {
				return null;
			}
			Iterator<MutableRoaringBitmap> iterator1 = tailMap1.values().iterator();
			return combineMaps(iterator1);
		case LESS_THAN_EQUALS:
			SortedMap<String, MutableRoaringBitmap> headMap1 = map
					.headMap(simpleFilter.getComparedValue() + Character.MAX_VALUE);
			if (headMap1.isEmpty()) {
				return null;
			}
			return combineMaps(headMap1.values().iterator());
		}
		return null;
	}

	private MutableRoaringBitmap combineMaps(Iterator<MutableRoaringBitmap> itr) {
		MutableRoaringBitmap resultMap = new MutableRoaringBitmap();
		while (itr.hasNext()) {
			MutableRoaringBitmap m = itr.next();
			resultMap.or(m);
		}
		return resultMap;
	}

	@Override
	public void close() throws IOException {
		rev.force();
		revRaf.close();
	}

	public MutableRoaringBitmap getBitMapForTag(String tagKey, String tagValue) {
		return rowKeyIndex.get(tagKey).get(tagValue);
	}

	@Override
	public void index(String tagKey, String tagValue, int rowIndex) throws IOException {
		logger.finest(() -> "Indexing tagKey:" + tagKey + " with tagValue:" + tagValue + " on rowIndex:" + rowIndex);
		SortedMap<String, MutableRoaringBitmap> tagValueMap = rowKeyIndex.get(tagKey);
		if (tagValueMap == null) {
			synchronized (rowKeyIndex) {
				if ((tagValueMap = rowKeyIndex.get(tagKey)) == null) {
					tagValueMap = new ConcurrentSkipListMap<>();
					rowKeyIndex.put(tagKey, tagValueMap);
				}
			}
		}

		MutableRoaringBitmap rowKeySet = tagValueMap.get(tagValue);
		if (rowKeySet == null) {
			synchronized (tagValueMap) {
				if ((rowKeySet = tagValueMap.get(tagValue)) == null) {
					rowKeySet = new MutableRoaringBitmap();
					tagValueMap.put(tagValue, rowKeySet);
					logger.finest(() -> "Not found tag value map creating it:" + tagKey + " with tagValue:" + tagValue
							+ " on rowIndex:" + rowIndex);
				}
			}
		}

		if (!rowKeySet.contains(rowIndex)) {
			boolean add = rowKeySet.checkedAdd(rowIndex);
			if (add) {
				if (enableMetrics) {
					metricIndexRow.inc();
				}
				synchronized (rowKeyIndex) {
					byte[] str = (tagKey + " " + tagValue + " " + rowIndex).getBytes();
					if (rev.remaining() < str.length + Integer.BYTES) {
						// resize buffer
						int temp = rev.position();
						rev = revRaf.getChannel().map(MapMode.READ_WRITE, 0, rev.capacity() + INCREMENT_SIZE);
						rev.position(temp);
					}
					rev.putInt(str.length);
					rev.put(str);
					rev.putInt(0, rev.position());
					logger.finest(() -> "Not found row index entry in bitmap for:" + tagKey + " with tagValue:"
							+ tagValue + " on rowIndex:" + rowIndex);
				}
			}
		}
	}

	@Override
	public int getSize() {
		int total = 0;
		for (Entry<String, SortedMap<String, MutableRoaringBitmap>> entry : rowKeyIndex.entrySet()) {
			for (Entry<String, MutableRoaringBitmap> entry2 : entry.getValue().entrySet()) {
				total += entry2.getValue().getSizeInBytes() + entry.getKey().length();
			}
		}
		return total;
	}

	@Override
	public void index(String tag, String value, String rowKey) throws IOException {
		// not implemented
		throw new UnsupportedOperationException("Bitmap index can't store string rowkeys");
	}

	@Override
	public Set<String> searchRowKeysForTagFilter(TagFilter tagFilterTree) {
		Set<String> rowKeys = new HashSet<>();
		MutableRoaringBitmap evalFilterForTags = evalFilterForTags(tagFilterTree);
		if (evalFilterForTags != null) {
			bitmapToRowKeys(rowKeys, evalFilterForTags);
		}
		return rowKeys;
	}

	@Override
	public Collection<String> getTagValues(String tagKey) {
		SortedMap<String, MutableRoaringBitmap> map = rowKeyIndex.get(tagKey);
		if (map != null) {
			return map.keySet();
		} else {
			return null;
		}
	}

}