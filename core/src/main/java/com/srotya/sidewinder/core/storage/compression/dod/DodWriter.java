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
package com.srotya.sidewinder.core.storage.compression.dod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.srotya.sidewinder.core.storage.DataPoint;
import com.srotya.sidewinder.core.storage.RejectException;
import com.srotya.sidewinder.core.storage.compression.Writer;

/**
 * A simple delta-of-delta timeseries compression with no value compression
 * 
 * @author ambud
 */
public class DodWriter implements Writer {

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private ReadLock read = lock.readLock();
	private WriteLock write = lock.writeLock();
	private BitWriter writer;
	private long prevTs;
	private long delta;
	private int count;
	private long lastTs;
	private boolean readOnly;
	private boolean full;
	private long prevValue = Long.MIN_VALUE;
	private long prevXor;
	private int startOffset;
	private String tsBucket;

	public DodWriter() {
	}

	public DodWriter(long headTs, byte[] buf) {
		prevTs = headTs;
		writer = new BitWriter(buf);
	}

	@Override
	public void configure(Map<String, String> conf, ByteBuffer buf, boolean isNew, int startOffset, boolean isLocking) throws IOException {
		this.startOffset = startOffset;
		buf.position(this.startOffset);
		writer = new BitWriter(buf);
	}

	public void write(DataPoint dp) throws RejectException {
		if (readOnly) {
			throw WRITE_REJECT_EXCEPTION;
		}
		try {
			write.lock();
			writeDataPoint(dp.getTimestamp(), dp.getLongValue());
		} finally {
			write.unlock();
		}
	}

	public void write(List<DataPoint> dps) {
		try {
			write.lock();
			for (Iterator<DataPoint> itr = dps.iterator(); itr.hasNext();) {
				DataPoint dp = itr.next();
				writeDataPoint(dp.getTimestamp(), dp.getLongValue());
			}
		} finally {
			write.unlock();
		}
	}

	/**
	 * 
	 * @param dp
	 */
	private void writeDataPoint(long timestamp, long value) {
		compressTimestamp(timestamp);
		compressValue(value);
		count++;
	}

	/**
	 * (a) Calculate the delta of delta: D = (tn - tn1) - (tn1 - tn2) (b) If D is
	 * zero, then store a single `0' bit (c) If D is between [-63, 64], store `10'
	 * followed by the value (7 bits) (d) If D is between [-255, 256], store `110'
	 * followed by the value (9 bits) (e) if D is between [-2047, 2048], store
	 * `1110' followed by the value (12 bits) (f) Otherwise store `1111' followed by
	 * D using 32 bits
	 * 
	 * @param timestamp
	 */
	private void compressTimestamp(long timestamp) {
		if (count == 0) {
			prevTs = timestamp;
			lastTs = timestamp;
			writer.writeBits(timestamp, 64);
		} else {
			lastTs = timestamp;
			long ts = timestamp;
			long newDelta = (ts - prevTs);
			int deltaOfDelta = (int) (newDelta - delta);
			if (deltaOfDelta == 0) {
				writer.writeBits(0, 1);
			} else if (deltaOfDelta >= -63 && deltaOfDelta <= 64) {
				writer.writeBits(2, 2);
				writer.writeBits(deltaOfDelta, 7);
			} else if (deltaOfDelta >= 255 && deltaOfDelta <= 256) {
				writer.writeBits(6, 3);
				writer.writeBits(deltaOfDelta, 9);
			} else if (deltaOfDelta >= 255 && deltaOfDelta <= 256) {
				writer.writeBits(14, 4);
				writer.writeBits(deltaOfDelta, 12);
			} else {
				writer.writeBits(15, 4);
				writer.writeBits(deltaOfDelta, 32);
			}
			prevTs = ts;
			delta = newDelta;
		}
	}

	private void compressValue(long value) {
		long xor = prevValue ^ value;
		if (count == 0) {
			writer.writeBits(value, 64);
		} else {
			if (xor == 0) {
				writer.writeBits(0, 1);
			} else {
				writer.writeBits(1, 1);
				int numberOfLeadingZeros = Long.numberOfLeadingZeros(xor);
				int numberOfTrailingZeros = Long.numberOfTrailingZeros(xor);

				int prevNumberOfLeadingZeros = Long.numberOfLeadingZeros(prevXor);
				int prevNumberOfTrailingZeros = Long.numberOfTrailingZeros(prevXor);

				if (numberOfLeadingZeros >= prevNumberOfLeadingZeros
						&& numberOfTrailingZeros >= prevNumberOfTrailingZeros) {
					writer.writeBits(0, 1);
					writer.writeBits(xor >>> prevNumberOfTrailingZeros,
							Long.SIZE - prevNumberOfLeadingZeros - prevNumberOfTrailingZeros);
				} else {
					writer.writeBits(1, 1);
					writer.writeBits(numberOfLeadingZeros, 6);
					writer.writeBits(Long.SIZE - numberOfLeadingZeros - numberOfTrailingZeros, 6);
					writer.writeBits((xor >>> numberOfTrailingZeros),
							Long.SIZE - numberOfLeadingZeros - numberOfTrailingZeros);
				}
			}
		}
		prevValue = value;
		prevXor = xor;
	}

	@Override
	public void addValue(long timestamp, double value) {
		addValue(timestamp, Double.doubleToLongBits(value));
	}

	public DoDReader getReader() {
		DoDReader reader = null;
		read.lock();
		ByteBuffer rbuf = writer.getBuffer().duplicate();
		reader = new DoDReader(rbuf, count, lastTs);
		read.unlock();
		return reader;
	}

	@Override
	public void addValue(long timestamp, long value) {
		try {
			write.lock();
			writeDataPoint(timestamp, value);
		} finally {
			write.unlock();
		}
	}

	@Override
	public double getCompressionRatio() {
		double ratio = 0;
		read.lock();
		ratio = ((double) count * Long.BYTES * 2) / writer.getBuffer().position();
		read.unlock();
		return ratio;
	}

	@Override
	public void setHeaderTimestamp(long timestamp) {
	}

	public BitWriter getWriter() {
		return writer;
	}

	@Override
	public ByteBuffer getRawBytes() {
		read.lock();
		ByteBuffer b = writer.getBuffer().duplicate();
		b.rewind();
		read.unlock();
		return b;
	}

	@Override
	public void bootstrap(ByteBuffer buf) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCounter(int counter) {
		this.count = counter;
	}

	@Override
	public void makeReadOnly() {
		write.lock();
		readOnly = true;
		write.unlock();
	}

	@Override
	public int currentOffset() {
		return 0;
	}

	@Override
	public int getCount() {
		return count;
	}

	@Override
	public boolean isFull() {
		return full;
	}

	@Override
	public long getHeaderTimestamp() {
		return 0;
	}

	@Override
	public void setTsBucket(String tsBucket) {
		this.tsBucket = tsBucket;
	}

	@Override
	public String getTsBucket() {
		return tsBucket;
	}

	@Override
	public int getPosition() {
		return -1;
	}

}