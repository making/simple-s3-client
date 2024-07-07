/*
 * Copyright (C) 2023 Toshiaki Maki <makingx@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package am.ik.s3;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "ListVersionsResult")
public final class ListVersionsResult {

	private final String name;

	private final String prefix;

	private final String keyMarker;

	private final String nextVersionIdMarker;

	private final String versionIdMarker;

	private final int maxKeys;

	private final boolean isTruncated;

	private List<DeleteMarker> deleteMarkers;

	private List<Version> versions;

	public ListVersionsResult(@JacksonXmlProperty(localName = "Name") String name,
			@JacksonXmlProperty(localName = "Prefix") String prefix,
			@JacksonXmlProperty(localName = "KeyMarker") String keyMarker,
			@JacksonXmlProperty(localName = "NextVersionIdMarker") String nextVersionIdMarker,
			@JacksonXmlProperty(localName = "VersionIdMarker") String versionIdMarker,
			@JacksonXmlProperty(localName = "MaxKeys") int maxKeys,
			@JacksonXmlProperty(localName = "IsTruncated") boolean isTruncated,
			@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(
					localName = "DeleteMarker") List<DeleteMarker> deleteMarkers,
			@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(
					localName = "Version") List<Version> versions) {
		this.name = name;
		this.prefix = prefix;
		this.keyMarker = keyMarker;
		this.nextVersionIdMarker = nextVersionIdMarker;
		this.versionIdMarker = versionIdMarker;
		this.maxKeys = maxKeys;
		this.isTruncated = isTruncated;
		this.deleteMarkers = deleteMarkers;
		this.versions = versions;
	}

	public String name() {
		return name;
	}

	public String prefix() {
		return prefix;
	}

	public String keyMarker() {
		return keyMarker;
	}

	public String nextVersionIdMarker() {
		return nextVersionIdMarker;
	}

	public String versionIdMarker() {
		return versionIdMarker;
	}

	public int maxKeys() {
		return maxKeys;
	}

	public boolean isTruncated() {
		return isTruncated;
	}

	public List<DeleteMarker> deleteMarkers() {
		return deleteMarkers;
	}

	public List<Version> versions() {
		return versions;
	}

	public void setVersions(List<Version> versions) {
		this.versions = versions;
	}

	public void setDeleteMarkers(List<DeleteMarker> deleteMarkers) {
		this.deleteMarkers = deleteMarkers;
	}

}