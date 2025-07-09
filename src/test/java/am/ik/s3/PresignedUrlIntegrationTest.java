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

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import static am.ik.s3.S3RequestBuilder.s3Request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Testcontainers
class PresignedUrlIntegrationTest {

	private static final String ACCESS_KEY = "minioadmin";

	private static final String SECRET_KEY = "minioadmin";

	@Container
	static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
		.withExposedPorts(9000)
		.withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
		.withEnv("MINIO_SECRET_KEY", SECRET_KEY)
		.withCommand("server", "/data");

	private S3Client client;

	private RestClient restClient;

	private static final String BUCKET_NAME = "test-bucket";

	private static final String OBJECT_KEY = "test-object.txt";

	private static final String TEST_CONTENT = "Hello, Presigned URL!";

	@BeforeEach
	void setUp() {
		String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));

		client = S3Client.builder().endpoint(endpoint).region("us-east-1").credentials(ACCESS_KEY, SECRET_KEY).build();

		restClient = RestClient.builder()
			.requestInterceptor(new LogbookClientHttpRequestInterceptor(Logbook.builder()
				.sink(new AccessLoggerSink())
				.headerFilter(OpinionatedFilters.headerFilter())
				.build()))
			.messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
			.build();

		// Create test bucket
		client.bucket(BUCKET_NAME).create();
	}

	@AfterEach
	void tearDown() {
		try {
			// Clean up test object
			client.bucket(BUCKET_NAME).object(OBJECT_KEY).delete();
		}
		catch (Exception e) {
			// Ignore if object doesn't exist
		}
		try {
			// Clean up test bucket
			client.bucket(BUCKET_NAME).delete();
		}
		catch (Exception e) {
			// Ignore if bucket doesn't exist
		}
	}

	@Test
	void testPresignedGetUrl() {
		// First upload an object
		client.bucket(BUCKET_NAME).object(OBJECT_KEY).put(TEST_CONTENT, MediaType.TEXT_PLAIN);

		// Generate presigned GET URL
		PresignedUrl getUrl = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.GET, Duration.ofMinutes(15));

		assertThat(getUrl.url()).isNotNull();
		assertThat(getUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(getUrl.url().toString()).contains("X-Amz-Expires=900");
		assertThat(getUrl.url().toString()).contains(BUCKET_NAME);
		assertThat(getUrl.url().toString()).contains(OBJECT_KEY);
		assertThat(getUrl.isExpired()).isFalse();

		// Use the presigned URL to download the object
		String downloadedContent = restClient.get()
			.uri(getUrl.url())
			.headers(getUrl.headers())
			.retrieve()
			.body(String.class);

		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testPresignedPutUrl() {
		// Generate presigned PUT URL without Content-Type header
		PresignedUrl putUrl = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.PUT, Duration.ofMinutes(15));

		assertThat(putUrl.url()).isNotNull();
		assertThat(putUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(putUrl.url().toString()).contains("X-Amz-Expires=900");
		assertThat(putUrl.url().toString()).contains(BUCKET_NAME);
		assertThat(putUrl.url().toString()).contains(OBJECT_KEY);
		assertThat(putUrl.isExpired()).isFalse();

		// Use the presigned URL to upload content
		restClient.put()
			.uri(putUrl.url())
			.headers(headers -> putUrl.requiredHeaders().forEach(headers::add))
			.body(TEST_CONTENT)
			.retrieve()
			.toBodilessEntity();

		// Verify the object was uploaded correctly
		String downloadedContent = client.bucket(BUCKET_NAME).object(OBJECT_KEY).getAsString();
		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testPresignedDeleteUrl() {
		// First upload an object
		client.bucket(BUCKET_NAME).object(OBJECT_KEY).put(TEST_CONTENT, MediaType.TEXT_PLAIN);

		// Verify object exists
		String content = client.bucket(BUCKET_NAME).object(OBJECT_KEY).getAsString();
		assertThat(content).isEqualTo(TEST_CONTENT);

		// Generate presigned DELETE URL
		PresignedUrl deleteUrl = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.DELETE, Duration.ofMinutes(15));

		assertThat(deleteUrl.url()).isNotNull();
		assertThat(deleteUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(deleteUrl.url().toString()).contains("X-Amz-Expires=900");
		assertThat(deleteUrl.url().toString()).contains(BUCKET_NAME);
		assertThat(deleteUrl.url().toString()).contains(OBJECT_KEY);
		assertThat(deleteUrl.isExpired()).isFalse();

		// Use the presigned URL to delete the object
		restClient.delete()
			.uri(deleteUrl.url())
			.headers(headers -> deleteUrl.requiredHeaders().forEach(headers::add))
			.retrieve()
			.toBodilessEntity();

		// Verify the object was deleted
		ListBucketResult listResult = client.bucket(BUCKET_NAME).listObjects();
		assertThat(listResult.contents()).isNullOrEmpty();
	}

	@Test
	void testPresignedPostForm() {
		// Generate presigned POST form
		PresignedPostForm postForm = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedPostForm(Duration.ofMinutes(15))
			.maxFileSize(1024 * 1024) // 1MB
			.addField("Content-Type", "text/plain")
			.generate();

		assertThat(postForm.url()).isNotNull();
		assertThat(postForm.url().toString()).contains(BUCKET_NAME);
		assertThat(postForm.formFields()).containsKey("key");
		assertThat(postForm.formFields()).containsKey("bucket");
		assertThat(postForm.formFields()).containsKey("X-Amz-Algorithm");
		assertThat(postForm.formFields()).containsKey("X-Amz-Credential");
		assertThat(postForm.formFields()).containsKey("X-Amz-Date");
		assertThat(postForm.formFields()).containsKey("policy");
		assertThat(postForm.formFields()).containsKey("X-Amz-Signature");
		assertThat(postForm.formFields()).containsEntry("Content-Type", "text/plain");
		assertThat(postForm.formFields()).containsEntry("key", OBJECT_KEY);
		assertThat(postForm.formFields()).containsEntry("bucket", BUCKET_NAME);

		// Use the presigned POST form to upload content
		restClient.post()
			.uri(postForm.url())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(createMultipartFormData(postForm.formFields(), TEST_CONTENT))
			.retrieve()
			.toBodilessEntity();

		// Verify the object was uploaded correctly
		String downloadedContent = client.bucket(BUCKET_NAME).object(OBJECT_KEY).getAsString();
		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testPresignedUrlWithCustomExpiration() {
		// Upload an object first
		client.bucket(BUCKET_NAME).object(OBJECT_KEY).put(TEST_CONTENT, MediaType.TEXT_PLAIN);

		// Generate presigned URL with custom expiration
		Duration customExpiration = Duration.ofHours(2);
		PresignedUrl getUrl = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.GET, customExpiration);

		assertThat(getUrl.url().toString()).contains("X-Amz-Expires=7200"); // 2 hours =
																			// 7200
																			// seconds
		assertThat(getUrl.url().toString()).contains(BUCKET_NAME);
		assertThat(getUrl.url().toString()).contains(OBJECT_KEY);
		assertThat(getUrl.isExpired()).isFalse();

		// Use the presigned URL
		String downloadedContent = restClient.get()
			.uri(getUrl.url())
			.headers(getUrl.headers())
			.retrieve()
			.body(String.class);

		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testPresignedPostFormWithDataSize() {
		// Generate presigned POST form with DataSize
		PresignedPostForm postForm = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedPostForm(Duration.ofMinutes(15))
			.maxFileSize(DataSize.ofMegabytes(5)) // 5MB using DataSize
			.addField("Content-Type", "text/plain")
			.generate();

		assertThat(postForm.url()).isNotNull();
		assertThat(postForm.url().toString()).contains(BUCKET_NAME);
		assertThat(postForm.formFields()).containsKey("key");
		assertThat(postForm.formFields()).containsKey("bucket");
		assertThat(postForm.formFields()).containsEntry("Content-Type", "text/plain");
		assertThat(postForm.formFields()).containsEntry("key", OBJECT_KEY);
		assertThat(postForm.formFields()).containsEntry("bucket", BUCKET_NAME);

		// Use the presigned POST form to upload content
		restClient.post()
			.uri(postForm.url())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(createMultipartFormData(postForm.formFields(), TEST_CONTENT))
			.retrieve()
			.toBodilessEntity();

		// Verify the object was uploaded correctly
		String downloadedContent = client.bucket(BUCKET_NAME).object(OBJECT_KEY).getAsString();
		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testPresignedUrlExpiration() throws InterruptedException {
		// First upload an object
		client.bucket(BUCKET_NAME).object(OBJECT_KEY).put(TEST_CONTENT, MediaType.TEXT_PLAIN);

		// Generate presigned GET URL with 1 second expiration
		PresignedUrl getUrl = client.bucket(BUCKET_NAME)
			.object(OBJECT_KEY)
			.presignedUrl(HttpMethod.GET, Duration.ofSeconds(1));

		assertThat(getUrl.url()).isNotNull();
		assertThat(getUrl.isExpired()).isFalse();

		// Use the presigned URL immediately - should work
		String downloadedContent = restClient.get()
			.uri(getUrl.url())
			.headers(getUrl.headers())
			.retrieve()
			.body(String.class);

		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);

		// Wait for 2 seconds to ensure expiration
		Thread.sleep(1000);

		// Check that the URL is now expired
		assertThat(getUrl.isExpired()).isTrue();

		// Try to use the expired URL
		var response = restClient.get()
			.uri(getUrl.url())
			.headers(getUrl.headers())
			.retrieve()
			.onStatus(stats -> stats.value() == 403, (req, res) -> {
			})
			.toEntity(String.class);
		assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
		assertThat(response.getBody()).contains("Request has expired");
	}

	@Test
	void testLowLevelApiPresignedUrl() {
		// First upload an object using Fluent API
		client.bucket(BUCKET_NAME).object(OBJECT_KEY).put(TEST_CONTENT, MediaType.TEXT_PLAIN);

		// Generate presigned GET URL using Low Level API
		String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));
		S3Request request = s3Request().endpoint(URI.create(endpoint))
			.region("us-east-1")
			.accessKeyId(ACCESS_KEY)
			.secretAccessKey(SECRET_KEY)
			.method(GET)
			.path(b -> b.bucket(BUCKET_NAME).key(OBJECT_KEY))
			.build();

		PresignedUrl presignedUrl = request.presignedUrl(Duration.ofMinutes(15));

		assertThat(presignedUrl.url()).isNotNull();
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Expires=900");
		assertThat(presignedUrl.url().toString()).contains(BUCKET_NAME);
		assertThat(presignedUrl.url().toString()).contains(OBJECT_KEY);
		assertThat(presignedUrl.isExpired()).isFalse();

		// Use the presigned URL to download the object
		String downloadedContent = restClient.get()
			.uri(presignedUrl.url())
			.headers(presignedUrl.headers())
			.retrieve()
			.body(String.class);

		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	@Test
	void testLowLevelApiPresignedPostForm() {
		// Generate presigned POST form using Low Level API
		String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));

		S3Request request = s3Request().endpoint(URI.create(endpoint))
			.region("us-east-1")
			.accessKeyId(ACCESS_KEY)
			.secretAccessKey(SECRET_KEY)
			.method(POST)
			.path(b -> b.bucket(BUCKET_NAME).key(OBJECT_KEY))
			.build();

		PresignedPostForm postForm = request.presignedPostForm(Duration.ofMinutes(15))
			.maxFileSize(DataSize.ofMegabytes(1))
			.addField("Content-Type", "text/plain")
			.generate();

		assertThat(postForm.url()).isNotNull();
		assertThat(postForm.url().toString()).contains(BUCKET_NAME);
		assertThat(postForm.formFields()).containsKey("key");
		assertThat(postForm.formFields()).containsKey("bucket");
		assertThat(postForm.formFields()).containsKey("X-Amz-Algorithm");
		assertThat(postForm.formFields()).containsKey("X-Amz-Credential");
		assertThat(postForm.formFields()).containsKey("X-Amz-Date");
		assertThat(postForm.formFields()).containsKey("policy");
		assertThat(postForm.formFields()).containsKey("X-Amz-Signature");
		assertThat(postForm.formFields()).containsEntry("Content-Type", "text/plain");
		assertThat(postForm.formFields()).containsEntry("key", OBJECT_KEY);
		assertThat(postForm.formFields()).containsEntry("bucket", BUCKET_NAME);

		// Use the presigned POST form to upload content
		restClient.post()
			.uri(postForm.url())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(createMultipartFormData(postForm.formFields(), TEST_CONTENT))
			.retrieve()
			.toBodilessEntity();

		// Verify the object was uploaded correctly
		String downloadedContent = client.bucket(BUCKET_NAME).object(OBJECT_KEY).getAsString();
		assertThat(downloadedContent).isEqualTo(TEST_CONTENT);
	}

	private MultiValueMap<String, Object> createMultipartFormData(Map<String, String> formFields, String fileContent) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

		// Add all form fields
		formFields.forEach(parts::add);

		// Add file content
		ByteArrayResource fileResource = new ByteArrayResource(fileContent.getBytes()) {
			@Override
			public String getFilename() {
				return OBJECT_KEY;
			}
		};
		parts.add("file", fileResource);

		return parts;
	}

}