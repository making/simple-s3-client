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

/**
 * Represents the result of listing object versions in an S3 bucket.
 */
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

	/**
	 * Creates a new ListVersionsResult.
	 * @param name the name of the bucket
	 * @param prefix the prefix used in the listing request
	 * @param keyMarker the key marker used for pagination
	 * @param nextVersionIdMarker the next version ID marker for pagination
	 * @param versionIdMarker the version ID marker used for pagination
	 * @param maxKeys the maximum number of keys returned
	 * @param isTruncated indicates if the result was truncated
	 * @param deleteMarkers the list of delete markers
	 * @param versions the list of object versions
	 */
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

	/**
	 * Returns the name of the bucket.
	 * @return the bucket name
	 */
	public String name() {
		return name;
	}

	/**
	 * Returns the prefix used in the listing request.
	 * @return the prefix
	 */
	public String prefix() {
		return prefix;
	}

	/**
	 * Returns the key marker used for pagination.
	 * @return the key marker
	 */
	public String keyMarker() {
		return keyMarker;
	}

	/**
	 * Returns the next version ID marker for pagination.
	 * @return the next version ID marker
	 */
	public String nextVersionIdMarker() {
		return nextVersionIdMarker;
	}

	/**
	 * Returns the version ID marker used for pagination.
	 * @return the version ID marker
	 */
	public String versionIdMarker() {
		return versionIdMarker;
	}

	/**
	 * Returns the maximum number of keys returned.
	 * @return the maximum keys
	 */
	public int maxKeys() {
		return maxKeys;
	}

	/**
	 * Indicates if the result was truncated.
	 * @return true if truncated, false otherwise
	 */
	public boolean isTruncated() {
		return isTruncated;
	}

	/**
	 * Returns the list of delete markers.
	 * @return the delete markers
	 */
	public List<DeleteMarker> deleteMarkers() {
		return deleteMarkers;
	}

	/**
	 * Returns the list of object versions.
	 * @return the versions
	 */
	public List<Version> versions() {
		return versions;
	}

	/**
	 * Sets the list of object versions.
	 * @param versions the versions to set
	 */
	public void setVersions(List<Version> versions) {
		this.versions = versions;
	}

	/**
	 * Sets the list of delete markers.
	 * @param deleteMarkers the delete markers to set
	 */
	public void setDeleteMarkers(List<DeleteMarker> deleteMarkers) {
		this.deleteMarkers = deleteMarkers;
	}

}