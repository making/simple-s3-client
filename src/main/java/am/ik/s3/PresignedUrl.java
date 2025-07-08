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

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static am.ik.s3.S3RequestBuilder.s3Request;

/**
 * Represents a presigned URL for S3 operations with expiration information.
 *
 * @since 0.3.0
 */
public record PresignedUrl(URI url, Instant expiresAt, Map<String, String> requiredHeaders) {

	/**
	 * Checks if this presigned URL has expired using the system default clock.
	 * @return true if the URL has expired, false otherwise
	 */
	public boolean isExpired() {
		return isExpired(Clock.systemUTC());
	}

	/**
	 * Checks if this presigned URL has expired using the specified clock.
	 * @param clock the clock to use for checking expiration
	 * @return true if the URL has expired, false otherwise
	 */
	public boolean isExpired(Clock clock) {
		return clock.instant().isAfter(expiresAt);
	}

	/**
	 * Returns a Consumer that adds required headers for this presigned URL. This is
	 * convenient for use with RestClient's headers() method.
	 * @return a Consumer that adds required headers to HttpHeaders
	 */
	public Consumer<HttpHeaders> headers() {
		return headers -> requiredHeaders.forEach(headers::add);
	}

	/**
	 * Creates a new builder for presigned URLs.
	 * @param configuration the S3 client configuration
	 * @param bucketName the bucket name
	 * @param objectKey the object key
	 * @return a new builder instance
	 */
	public static Builder builder(S3ClientConfiguration configuration, String bucketName, String objectKey) {
		return new Builder(configuration, bucketName, objectKey);
	}

	/**
	 * Builder for generating presigned URLs for S3 operations.
	 *
	 * @since 0.3.0
	 */
	public static class Builder {

		private final S3ClientConfiguration configuration;

		private final String bucketName;

		private final String objectKey;

		private Duration expiration = Duration.ofHours(1);

		private final Map<String, String> requestHeaders = new HashMap<>();

		private final Map<String, String> requestParameters = new HashMap<>();

		private Builder(S3ClientConfiguration configuration, String bucketName, String objectKey) {
			this.configuration = configuration;
			this.bucketName = bucketName;
			this.objectKey = objectKey;
		}

		/**
		 * Sets the expiration duration for the presigned URL.
		 * @param expiration the duration until the URL expires
		 * @return this builder
		 */
		public Builder expiration(Duration expiration) {
			this.expiration = expiration;
			return this;
		}

		/**
		 * Adds a request header to be included in the presigned URL.
		 * @param name the header name
		 * @param value the header value
		 * @return this builder
		 */
		public Builder header(String name, String value) {
			this.requestHeaders.put(name, value);
			return this;
		}

		/**
		 * Adds a request parameter to be included in the presigned URL.
		 * @param name the parameter name
		 * @param value the parameter value
		 * @return this builder
		 */
		public Builder parameter(String name, String value) {
			this.requestParameters.put(name, value);
			return this;
		}

		/**
		 * Sets the content type for the presigned URL (useful for PUT operations).
		 * @param contentType the content type
		 * @return this builder
		 */
		public Builder contentType(String contentType) {
			this.requestHeaders.put("Content-Type", contentType);
			return this;
		}

		/**
		 * Sets the content type for the presigned URL (useful for PUT operations).
		 * @param mediaType the media type
		 * @return this builder
		 */
		public Builder contentType(MediaType mediaType) {
			return contentType(mediaType.toString());
		}

		/**
		 * Generates a presigned URL for GET operation.
		 * @return the presigned URL
		 */
		public PresignedUrl forGet() {
			return generatePresignedUrl(HttpMethod.GET);
		}

		/**
		 * Generates a presigned URL for PUT operation.
		 * @return the presigned URL
		 */
		public PresignedUrl forPut() {
			return generatePresignedUrl(HttpMethod.PUT);
		}

		/**
		 * Generates a presigned URL for DELETE operation.
		 * @return the presigned URL
		 */
		public PresignedUrl forDelete() {
			return generatePresignedUrl(HttpMethod.DELETE);
		}

		private PresignedUrl generatePresignedUrl(HttpMethod method) {
			String queryString = requestParameters.isEmpty() ? null
					: requestParameters.entrySet()
						.stream()
						.map(e -> e.getKey() + "=" + e.getValue())
						.reduce((a, b) -> a + "&" + b)
						.orElse("");

			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(method)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.canonicalQueryString(queryString)
				.build();

			return request.generatePresignedUrl(expiration, requestHeaders.isEmpty() ? null : requestHeaders);
		}

	}

}