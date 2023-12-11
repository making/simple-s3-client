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

@JacksonXmlRootElement(localName = "ListBucketResult")
public record ListBucketResult(@JacksonXmlProperty(localName = "Name") String name,
		@JacksonXmlProperty(localName = "Prefix") String prefix,
		@JacksonXmlProperty(localName = "Marker") String marker, @JacksonXmlProperty(localName = "MaxKeys") int maxKeys,
		@JacksonXmlProperty(localName = "IsTruncated") boolean isTruncated, @JacksonXmlProperty(
				localName = "Contents") @JacksonXmlElementWrapper(useWrapping = false) List<Content> contents) {
}
