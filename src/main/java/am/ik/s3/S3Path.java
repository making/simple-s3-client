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

import java.util.Objects;

import org.jilt.Builder;

import org.springframework.web.util.UriComponentsBuilder;

@Builder
public final class S3Path {

	private final String bucket;

	private final String key;

	private final Boolean encodeKey;

	public S3Path(String bucket, String key, Boolean encodeKey) {
		this.bucket = bucket;
		this.key = key;
		this.encodeKey = Objects.requireNonNullElse(encodeKey, true);
	}

	public String toCanonicalUri() {
		StringBuilder builder = new StringBuilder();
		if (bucket == null || bucket.isEmpty()) {
			builder.append("/");
		}
		else {
			if (!bucket.startsWith("/")) {
				builder.append("/");
			}
			builder.append(bucket);
		}
		if (key != null && !key.isEmpty()) {
			if (!key.startsWith("/")) {
				builder.append("/");
			}
			if (encodeKey) {
				builder.append(encodeKey(key));
			}
			else {
				builder.append(key);
			}
		}
		return builder.toString();
	}

	private static String encodeKey(String key) {
		String encodedKey = UriComponentsBuilder.fromPath(key).encode().build().getPath();
		if (encodedKey == null) {
			return null;
		}
		return encodedKey.replace("!", "%21")
			.replace("#", "%23")
			.replace("$", "%24")
			.replace("%", "%25")
			.replace("&", "%26")
			.replace("'", "%27")
			.replace("(", "%28")
			.replace(")", "%29")
			.replace("*", "%2A")
			.replace("+", "%2B")
			.replace(",", "%2C")
			.replace(":", "%3A")
			.replace(";", "%3B")
			.replace("=", "%3D")
			.replace("@", "%40")
			.replace("[", "%5B")
			.replace("]", "%5D")
			.replace("{", "%7B")
			.replace("}", "%7D");
	}

}
