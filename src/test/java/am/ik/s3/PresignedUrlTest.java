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
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static am.ik.s3.S3RequestBuilder.s3Request;
import static org.assertj.core.api.Assertions.assertThat;

class PresignedUrlTest {

	private static final String ENDPOINT = "https://s3.amazonaws.com";

	private static final String REGION = "us-east-1";

	private static final String ACCESS_KEY_ID = "dummy-access-key";

	private static final String SECRET_ACCESS_KEY = "dummy-secret-access-key";

	private static final String BUCKET = "test-bucket";

	private static final String OBJECT_KEY = "test-object.txt";

	private static final Instant FIXED_TIME = Instant.parse("2023-12-25T12:00:00Z");

	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

	@Test
	void testPresignedUrlGeneration() {
		S3Request request = s3Request().endpoint(URI.create(ENDPOINT))
			.region(REGION)
			.accessKeyId(ACCESS_KEY_ID)
			.secretAccessKey(SECRET_ACCESS_KEY)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(BUCKET).key(OBJECT_KEY))
			.clock(FIXED_CLOCK)
			.build();

		Duration expiration = Duration.ofHours(1);
		PresignedUrl presignedUrl = request.presignedUrl(expiration);

		assertThat(presignedUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Credential=" + ACCESS_KEY_ID);
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Date=20231225T120000Z");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Expires=3600");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-SignedHeaders=host");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Signature=");
		assertThat(presignedUrl.expiresAt()).isEqualTo(FIXED_TIME.plus(expiration));
	}

	@Test
	void testPresignedUrlWithAdditionalHeaders() {
		S3Request request = s3Request().endpoint(URI.create(ENDPOINT))
			.region(REGION)
			.accessKeyId(ACCESS_KEY_ID)
			.secretAccessKey(SECRET_ACCESS_KEY)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(BUCKET).key(OBJECT_KEY))
			.clock(FIXED_CLOCK)
			.build();

		Duration expiration = Duration.ofMinutes(30);
		Map<String, String> headers = Map.of("Content-Type", "application/json");
		PresignedUrl presignedUrl = request.presignedUrl(expiration, headers);

		assertThat(presignedUrl.url().toString()).contains("X-Amz-SignedHeaders=content-type;host");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Expires=1800");
		assertThat(presignedUrl.requiredHeaders()).containsEntry("Content-Type", "application/json");
	}

	@Test
	void testPresignedUrlExpiration() {
		Instant now = Instant.now();
		Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

		PresignedUrl expiredUrl = new PresignedUrl(URI.create("https://example.com"), now.minusSeconds(1), Map.of());
		assertThat(expiredUrl.isExpired()).isTrue();
		assertThat(expiredUrl.isExpired(fixedClock)).isTrue();

		PresignedUrl validUrl = new PresignedUrl(URI.create("https://example.com"), now.plusSeconds(3600), Map.of());
		assertThat(validUrl.isExpired()).isFalse();
		assertThat(validUrl.isExpired(fixedClock)).isFalse();

		// Test with a clock set to future time
		Clock futureClock = Clock.fixed(now.plusSeconds(3700), ZoneId.of("UTC"));
		assertThat(validUrl.isExpired(futureClock)).isTrue();
	}

	@Test
	void testPresignedUrlBuilderWithS3Client() {
		S3Client client = S3Client.builder()
			.endpoint(ENDPOINT)
			.region(REGION)
			.credentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY)
			.build();

		PresignedUrl getUrl = client.bucket(BUCKET)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.GET, Duration.ofHours(2));

		assertThat(getUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(getUrl.url().toString()).contains("X-Amz-Expires=7200");

		PresignedUrl putUrl = client.bucket(BUCKET)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.PUT, Duration.ofMinutes(15), Map.of("Content-Type", "application/json"));

		assertThat(putUrl.url().toString()).contains("X-Amz-Expires=900");
		assertThat(putUrl.requiredHeaders()).containsEntry("Content-Type", "application/json");
	}

	@Test
	void testPresignedUrlHeadersMethod() {
		Map<String, String> headers = Map.of("Content-Type", "application/json", "x-amz-meta-test", "value");
		PresignedUrl presignedUrl = new PresignedUrl(URI.create("https://example.com/test"),
				Instant.now().plusSeconds(3600), headers);

		HttpHeaders httpHeaders = new HttpHeaders();
		presignedUrl.headers().accept(httpHeaders);

		assertThat(httpHeaders.get("Content-Type")).containsExactly("application/json");
		assertThat(httpHeaders.get("x-amz-meta-test")).containsExactly("value");
	}

}