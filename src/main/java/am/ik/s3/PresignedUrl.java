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
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * Represents a presigned URL for S3 operations with expiration information.
 *
 * @param url the presigned URL
 * @param expiresAt the instant when this presigned URL expires
 * @param requiredHeaders headers that must be included when using this presigned URL
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

}