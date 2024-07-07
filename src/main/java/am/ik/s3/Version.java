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

public record Version(@JacksonXmlProperty(localName = "Key") String key,
		@JacksonXmlProperty(localName = "LastModified") String lastModified,
		@JacksonXmlProperty(localName = "ETag") String eTag, @JacksonXmlProperty(localName = "Size") int size,
		@JacksonXmlProperty(localName = "Owner") Owner owner,
		@JacksonXmlProperty(localName = "StorageClass") String storageClass,
		@JacksonXmlProperty(localName = "IsLatest") boolean isLatest,
		@JacksonXmlProperty(localName = "VersionId") String versionId) {
}