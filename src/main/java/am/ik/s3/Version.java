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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Represents a version of an S3 object.
 *
 * @param key the key (name) of the object
 * @param lastModified the date and time when the version was last modified
 * @param eTag the entity tag (ETag) of the version
 * @param size the size of the version in bytes
 * @param owner the owner of the version
 * @param storageClass the storage class of the version
 * @param isLatest indicates if this is the latest version
 * @param versionId the version ID of the object
 */
public record Version(@JacksonXmlProperty(localName = "Key") String key,
		@JacksonXmlProperty(localName = "LastModified") String lastModified,
		@JacksonXmlProperty(localName = "ETag") String eTag, @JacksonXmlProperty(localName = "Size") int size,
		@JacksonXmlProperty(localName = "Owner") Owner owner,
		@JacksonXmlProperty(localName = "StorageClass") String storageClass,
		@JacksonXmlProperty(localName = "IsLatest") boolean isLatest,
		@JacksonXmlProperty(localName = "VersionId") String versionId) {
}