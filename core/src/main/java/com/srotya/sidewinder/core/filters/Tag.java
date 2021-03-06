/**
 * Copyright 2018 Ambud Sharma
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
package com.srotya.sidewinder.core.filters;

public class Tag implements Comparable<Tag> {

	private String tagKey;
	private String tagValue;

	public Tag(String tagKey, String tagValue) {
		this.tagKey = tagKey;
		this.tagValue = tagValue;
	}

	@Override
	public int hashCode() {
		return (tagKey + "=" + tagValue).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		Tag o = ((Tag) obj);
		if(getTagKey().equals(o.getTagKey()) && getTagValue().equals(o.getTagValue())) {
			return true;
		}else {
			return false;
		}
	}

	/**
	 * @return the tagKey
	 */
	public String getTagKey() {
		return tagKey;
	}

	/**
	 * @param tagKey
	 *            the tagKey to set
	 */
	public void setTagKey(String tagKey) {
		this.tagKey = tagKey;
	}

	/**
	 * @return the tagValue
	 */
	public String getTagValue() {
		return tagValue;
	}

	/**
	 * @param tagValue
	 *            the tagValue to set
	 */
	public void setTagValue(String tagValue) {
		this.tagValue = tagValue;
	}

	@Override
	public int compareTo(Tag o) {
		int r = getTagKey().compareTo(o.getTagKey());
		if (r != 0) {
			return r;
		} else {
			return getTagValue().compareTo(o.getTagValue());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Tag [tagKey=" + tagKey + ", tagValue=" + tagValue + "]";
	}

}
