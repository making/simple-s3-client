package am.ik.s3;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.core.WithoutBodyStrategy;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import static am.ik.s3.S3RequestBuilder.s3Request;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StreamingIntegrationTest {

	@Container
	static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest").withExposedPorts(9000)
		.withEnv("MINIO_ROOT_USER", "minioadmin")
		.withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
		.withCommand("server /data");

	private S3Client s3Client;

	private final RestClient restClient = RestClient.builder()
		.requestInterceptor(new LogbookClientHttpRequestInterceptor(Logbook.builder()
			.sink(new AccessLoggerSink())
			.headerFilter(OpinionatedFilters.headerFilter())
			.strategy(new WithoutBodyStrategy())
			.build()))
		.messageConverters(converters -> {
			converters.add(new MappingJackson2XmlHttpMessageConverter());
			converters.add(new ResourceHttpMessageConverter());
		})
		.build();

	private URI endpoint;

	private final String accessKeyId = "minioadmin";

	private final String secretAccessKey = "minioadmin";

	private final String bucketName = "test-streaming";

	@BeforeEach
	void setUp() {
		this.endpoint = URI.create("http://localhost:" + minio.getMappedPort(9000));
		this.s3Client = S3Client.builder()
			.endpoint(endpoint)
			.region("us-east-1")
			.credentials(accessKeyId, secretAccessKey)
			.restClient(restClient)
			.build();

		// Create bucket if it doesn't exist
		try {
			s3Client.bucket(bucketName).create();
		}
		catch (Exception e) {
			// Bucket may already exist
		}
	}

	@Test
	void testStreamingGetAndPut() throws IOException {
		String objectKey = "streaming-test.txt";
		String content = "Hello, World! This is a streaming test.";
		byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

		// Upload using streaming PUT
		try (InputStream inputStream = new ByteArrayInputStream(contentBytes)) {
			boolean success = s3Client.bucket(bucketName)
				.object(objectKey)
				.putStream(inputStream, contentBytes.length, MediaType.TEXT_PLAIN);
			assertThat(success).isTrue();
		}

		// Download using streaming GET
		try (InputStream downloadStream = s3Client.bucket(bucketName).object(objectKey).getAsStream()) {
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(new String(downloadedBytes, StandardCharsets.UTF_8)).isEqualTo(content);
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testRangeRequest() throws IOException {
		String objectKey = "range-test.txt";
		String content = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

		// Upload content
		s3Client.bucket(bucketName).object(objectKey).put(content, MediaType.TEXT_PLAIN);

		// Test range request (bytes 10-19)
		try (InputStream rangeStream = s3Client.bucket(bucketName).object(objectKey).range(10, 19).getAsStream()) {
			String rangeContent = new String(rangeStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(rangeContent).isEqualTo("ABCDEFGHIJ");
		}

		// Test range request from position (bytes 30 to end)
		try (InputStream rangeStream = s3Client.bucket(bucketName).object(objectKey).rangeFrom(30).getAsStream()) {
			String rangeContent = new String(rangeStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(rangeContent).isEqualTo("UVWXYZ");
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testStreamingWithLargerData() throws IOException {
		String objectKey = "large-streaming-test.dat";
		byte[] largeData = new byte[1024 * 1024]; // 1MB
		for (int i = 0; i < largeData.length; i++) {
			largeData[i] = (byte) (i % 256);
		}

		// Upload using streaming PUT
		try (InputStream inputStream = new ByteArrayInputStream(largeData)) {
			boolean success = s3Client.bucket(bucketName).object(objectKey).putStream(inputStream, largeData.length);
			assertThat(success).isTrue();
		}

		// Download using streaming GET and verify
		try (InputStream downloadStream = s3Client.bucket(bucketName).object(objectKey).getAsStream()) {
			byte[] downloadedData = downloadStream.readAllBytes();
			assertThat(downloadedData).isEqualTo(largeData);
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testLowLevelStreamingOperations() throws IOException {
		String objectKey = "low-level-streaming-test.txt";
		String content = "This is a test for low-level streaming operations with RestClient.";
		byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

		// Upload using low-level streaming PUT with S3Content.ofStream
		try (InputStream inputStream = new ByteArrayInputStream(contentBytes)) {
			S3Request putStreamRequest = s3Request().endpoint(endpoint)
				.region("us-east-1")
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.ofStream(contentBytes.length, MediaType.TEXT_PLAIN))
				.build();

			restClient.put()
				.uri(putStreamRequest.uri())
				.headers(putStreamRequest.headers())
				.body(new InputStreamResource(inputStream))
				.retrieve()
				.toBodilessEntity();
		}

		// Download using low-level streaming GET
		S3Request getStreamRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build();

		org.springframework.core.io.Resource resource = restClient.get()
			.uri(getStreamRequest.uri())
			.headers(getStreamRequest.headers())
			.retrieve()
			.body(org.springframework.core.io.Resource.class);

		try (InputStream downloadStream = resource.getInputStream()) {
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(new String(downloadedBytes, StandardCharsets.UTF_8)).isEqualTo(content);
		}

		// Test range request with low-level API
		S3Request rangeRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build()
			.withRange(5, 14);

		org.springframework.core.io.Resource rangeResource = restClient.get()
			.uri(rangeRequest.uri())
			.headers(rangeRequest.headers())
			.retrieve()
			.body(org.springframework.core.io.Resource.class);

		try (InputStream rangeStream = rangeResource.getInputStream()) {
			String rangeContent = new String(rangeStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(rangeContent).isEqualTo("is a test ");
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

}