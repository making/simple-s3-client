package am.ik.s3;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
		String objectKey = "streaming-test.png";

		// Upload using streaming PUT with ClassPathResource
		Resource resource = new ClassPathResource("test.png");
		s3Client.bucket(bucketName).object(objectKey).putResource(resource, MediaType.IMAGE_PNG);

		// Download using streaming GET
		Resource downloadResource = s3Client.bucket(bucketName).object(objectKey).getAsResource();
		try (InputStream originalStream = resource.getInputStream();
				InputStream downloadStream = downloadResource.getInputStream()) {
			byte[] originalBytes = originalStream.readAllBytes();
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(downloadedBytes).isEqualTo(originalBytes);
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
		Resource rangeResource = s3Client.bucket(bucketName).object(objectKey).range(10, 19).getAsResource();
		try (InputStream rangeStream = rangeResource.getInputStream()) {
			String rangeContent = new String(rangeStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(rangeContent).isEqualTo("ABCDEFGHIJ");
		}

		// Test range request from position (bytes 30 to end)
		Resource rangeFromResource = s3Client.bucket(bucketName).object(objectKey).rangeFrom(30).getAsResource();
		try (InputStream rangeStream = rangeFromResource.getInputStream()) {
			String rangeContent = new String(rangeStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(rangeContent).isEqualTo("UVWXYZ");
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testStreamingWithLargerData() throws IOException {
		String objectKey = "large-streaming-test.png";

		// Upload using streaming PUT with ClassPathResource
		Resource resource = new ClassPathResource("test.png");
		s3Client.bucket(bucketName).object(objectKey).putResource(resource);

		// Download using streaming GET and verify
		Resource downloadResource = s3Client.bucket(bucketName).object(objectKey).getAsResource();
		try (InputStream originalStream = resource.getInputStream();
				InputStream downloadStream = downloadResource.getInputStream()) {
			byte[] originalData = originalStream.readAllBytes();
			byte[] downloadedData = downloadStream.readAllBytes();
			assertThat(downloadedData).isEqualTo(originalData);
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testLowLevelStreamingOperations() throws IOException {
		String objectKey = "low-level-streaming-test.png";

		// Upload using low-level streaming PUT with S3Content.ofResource
		Resource uploadResource = new ClassPathResource("test.png");
		S3Request putStreamRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.content(S3Content.ofResource(uploadResource, MediaType.IMAGE_PNG))
			.build();

		restClient.put()
			.uri(putStreamRequest.uri())
			.headers(putStreamRequest.headers())
			.body(uploadResource)
			.retrieve()
			.toBodilessEntity();

		// Download using low-level streaming GET
		S3Request getStreamRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build();

		Resource resource = restClient.get()
			.uri(getStreamRequest.uri())
			.headers(getStreamRequest.headers())
			.retrieve()
			.body(Resource.class);

		try (InputStream originalStream = uploadResource.getInputStream();
				InputStream downloadStream = Objects.requireNonNull(resource).getInputStream()) {
			byte[] originalBytes = originalStream.readAllBytes();
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(downloadedBytes).isEqualTo(originalBytes);
		}

		// Test range request with low-level API (first 100 bytes)
		S3Request rangeRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build()
			.withRange(0, 99);

		Resource rangeResource = restClient.get()
			.uri(rangeRequest.uri())
			.headers(rangeRequest.headers())
			.retrieve()
			.body(Resource.class);

		try (InputStream originalStream = uploadResource.getInputStream();
				InputStream rangeStream = Objects.requireNonNull(rangeResource).getInputStream()) {
			byte[] originalBytes = originalStream.readAllBytes();
			byte[] rangeBytes = rangeStream.readAllBytes();
			assertThat(rangeBytes).hasSize(100);
			assertThat(rangeBytes).isEqualTo(java.util.Arrays.copyOfRange(originalBytes, 0, 100));
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testStreamingUploadWithPresignedUrlDownload() throws IOException {
		String objectKey = "presigned-streaming-test.png";

		// Upload using streaming PUT with ClassPathResource
		Resource uploadResource = new ClassPathResource("test.png");
		s3Client.bucket(bucketName).object(objectKey).putResource(uploadResource, MediaType.IMAGE_PNG);

		// Generate presigned URL for download
		PresignedUrl presignedUrl = s3Client.bucket(bucketName)
			.object(objectKey)
			.presignedUrl(HttpMethod.GET, java.time.Duration.ofMinutes(5));

		assertThat(presignedUrl.url()).isNotNull();
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Signature=");

		// Download using presigned URL with RestClient
		Resource downloadedResource = restClient.get()
			.uri(presignedUrl.url())
			.headers(presignedUrl.headers())
			.retrieve()
			.body(Resource.class);

		try (InputStream originalStream = uploadResource.getInputStream();
				InputStream downloadStream = downloadedResource.getInputStream()) {
			byte[] originalBytes = originalStream.readAllBytes();
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(downloadedBytes).isEqualTo(originalBytes);
		}

		// Clean up
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testLowLevelStreamingUploadWithPresignedUrlDownload() throws IOException {
		String objectKey = "low-level-presigned-streaming-test.png";

		// Upload using low-level streaming PUT with S3Content.ofResource
		Resource uploadResource = new ClassPathResource("test.png");
		S3Request putStreamRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.content(S3Content.ofResource(uploadResource, MediaType.IMAGE_PNG))
			.build();

		restClient.put()
			.uri(putStreamRequest.uri())
			.headers(putStreamRequest.headers())
			.body(uploadResource)
			.retrieve()
			.toBodilessEntity();

		// Generate presigned URL using low-level API
		S3Request getRequest = s3Request().endpoint(endpoint)
			.region("us-east-1")
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build();

		PresignedUrl presignedUrl = getRequest.presignedUrl(java.time.Duration.ofMinutes(5));

		assertThat(presignedUrl.url()).isNotNull();
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
		assertThat(presignedUrl.url().toString()).contains("X-Amz-Signature=");

		// Download using presigned URL with RestClient
		Resource downloadedResource = restClient.get()
			.uri(presignedUrl.url())
			.headers(presignedUrl.headers())
			.retrieve()
			.body(Resource.class);

		try (InputStream originalStream = uploadResource.getInputStream();
				InputStream downloadStream = downloadedResource.getInputStream()) {
			byte[] originalBytes = originalStream.readAllBytes();
			byte[] downloadedBytes = downloadStream.readAllBytes();
			assertThat(downloadedBytes).isEqualTo(originalBytes);
		}

		// Clean up using S3Client for convenience
		s3Client.bucket(bucketName).object(objectKey).delete();
	}

}