package am.ik.s3;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.core.WithoutBodyStrategy;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MultipartUploadIntegrationTest {

	@Container
	static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest").withExposedPorts(9000)
		.withEnv("MINIO_ROOT_USER", "minioadmin")
		.withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
		.withCommand("server /data");

	private S3Client client;

	private String bucketName;

	private final RestClient restClient = RestClient.builder()
		.requestInterceptor(new LogbookClientHttpRequestInterceptor(Logbook.builder()
			.sink(new AccessLoggerSink())
			.headerFilter(OpinionatedFilters.headerFilter())
			.strategy(new WithoutBodyStrategy())
			.build()))
		.messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
		.build();

	@BeforeEach
	void setUp() {
		String endpoint = "http://localhost:" + minio.getMappedPort(9000);
		client = S3Client.builder()
			.endpoint(endpoint)
			.region("us-east-1")
			.credentials("minioadmin", "minioadmin")
			.restClient(restClient)
			.build();

		bucketName = "test-multipart-bucket-" + System.currentTimeMillis();
		client.bucket(bucketName).create();
	}

	@Test
	void testMultipartUploadLargeFile() {
		String objectKey = "large-file.txt";

		// Create a 15MB file (3 parts of 5MB each)
		byte[] data = createTestData(15 * 1024 * 1024);

		// Track progress
		AtomicInteger completedParts = new AtomicInteger(0);
		AtomicLong totalBytesTransferred = new AtomicLong(0);
		AtomicLong uploadCompletedTotalBytes = new AtomicLong(0);

		ProgressCallback progressCallback = new ProgressCallback() {
			@Override
			public void onPartCompleted(int partNumber, long bytesTransferred) {
				completedParts.incrementAndGet();
				totalBytesTransferred.addAndGet(bytesTransferred);
			}

			@Override
			public void onUploadCompleted(long totalBytes) {
				uploadCompletedTotalBytes.set(totalBytes);
			}

			@Override
			public void onError(Exception error) {
				throw new RuntimeException("Upload failed", error);
			}
		};

		// Perform multipart upload
		CompleteMultipartUploadResult result = client.bucket(bucketName)
			.object(objectKey)
			.multipartUpload()
			.partSize(DataSize.ofMegabytes(5))
			.maxConcurrentUploads(2)
			.progressCallback(progressCallback)
			.upload(data);

		// Verify the upload
		assertThat(result).isNotNull();
		assertThat(result.bucket()).isEqualTo(bucketName);
		assertThat(result.key()).isEqualTo(objectKey);
		assertThat(result.etag()).isNotNull();

		// Verify progress tracking
		assertThat(completedParts.get()).isEqualTo(3); // 3 parts
		assertThat(totalBytesTransferred.get()).isEqualTo(data.length);
		assertThat(uploadCompletedTotalBytes.get()).isEqualTo(data.length);

		// Verify the uploaded content
		byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
		assertThat(downloadedData).isEqualTo(data);

		// Verify data integrity with MD5 hash
		String originalMd5 = calculateMd5(data);
		String downloadedMd5 = calculateMd5(downloadedData);
		assertThat(downloadedMd5).isEqualTo(originalMd5);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testMultipartUploadWithInputStream() {
		String objectKey = "stream-file.txt";

		// Create a 12MB file
		byte[] data = createTestData(12 * 1024 * 1024);
		ByteArrayResource resource = new ByteArrayResource(data);

		// Perform multipart upload
		CompleteMultipartUploadResult result = client.bucket(bucketName)
			.object(objectKey)
			.multipartUpload()
			.partSize(DataSize.ofMegabytes(6))
			.maxConcurrentUploads(1) // Sequential upload
			.upload(resource);

		// Verify the upload
		assertThat(result).isNotNull();
		assertThat(result.bucket()).isEqualTo(bucketName);
		assertThat(result.key()).isEqualTo(objectKey);

		// Verify the uploaded content
		byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
		assertThat(downloadedData).isEqualTo(data);

		// Verify data integrity with MD5 hash
		String originalMd5 = calculateMd5(data);
		String downloadedMd5 = calculateMd5(downloadedData);
		assertThat(downloadedMd5).isEqualTo(originalMd5);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testMultipartUploadSmallFile() {
		String objectKey = "small-file.txt";

		// Create a 6MB file (just above the minimum part size)
		byte[] data = createTestData(6 * 1024 * 1024);

		// Perform multipart upload
		CompleteMultipartUploadResult result = client.bucket(bucketName)
			.object(objectKey)
			.multipartUpload()
			.upload(data);

		// Verify the upload
		assertThat(result).isNotNull();
		assertThat(result.bucket()).isEqualTo(bucketName);
		assertThat(result.key()).isEqualTo(objectKey);

		// Verify the uploaded content
		byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
		assertThat(downloadedData).isEqualTo(data);

		// Verify data integrity with MD5 hash
		String originalMd5 = calculateMd5(data);
		String downloadedMd5 = calculateMd5(downloadedData);
		assertThat(downloadedMd5).isEqualTo(originalMd5);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testMultipartUploadWithLowLevelApi() {
		String objectKey = "low-level-multipart-file.txt";

		// Create a 12MB file (2 parts of 6MB each)
		byte[] data = createTestData(12 * 1024 * 1024);

		String endpoint = "http://localhost:" + minio.getMappedPort(9000);
		S3Request baseRequest = S3RequestBuilder.s3Request()
			.endpoint(java.net.URI.create(endpoint))
			.region("us-east-1")
			.accessKeyId("minioadmin")
			.secretAccessKey("minioadmin")
			.method(org.springframework.http.HttpMethod.PUT)
			.path(b -> b.bucket(bucketName).key(objectKey))
			.build();

		try {
			// Step 1: Initiate multipart upload
			S3Request initiateRequest = baseRequest.initiateMultipartUpload();
			InitiateMultipartUploadResult initResult = restClient.post()
				.uri(initiateRequest.uri())
				.headers(initiateRequest.headers())
				.retrieve()
				.body(InitiateMultipartUploadResult.class);
			String uploadId = Objects.requireNonNull(initResult).uploadId();

			// Step 2: Upload parts
			java.util.List<CompletedPart> completedParts = new java.util.ArrayList<>();
			int partSize = 6 * 1024 * 1024; // 6MB per part
			int partNumber = 1;

			for (int offset = 0; offset < data.length; offset += partSize) {
				int currentPartSize = Math.min(partSize, data.length - offset);
				byte[] partData = java.util.Arrays.copyOfRange(data, offset, offset + currentPartSize);

				S3Request uploadPartRequest = baseRequest.uploadPart(uploadId, partNumber, partData);
				var partResponse = restClient.put()
					.uri(uploadPartRequest.uri())
					.headers(uploadPartRequest.headers())
					.body(partData)
					.retrieve()
					.toBodilessEntity();

				String etag = Objects.requireNonNull(partResponse.getHeaders().getFirst("ETag"));
				if (etag.startsWith("\"") && etag.endsWith("\"")) {
					etag = etag.substring(1, etag.length() - 1);
				}
				completedParts.add(new CompletedPart(etag, partNumber));
				partNumber++;
			}

			// Step 3: Complete multipart upload
			CompleteMultipartUpload completeRequest = new CompleteMultipartUpload(completedParts);
			S3Request completeUploadRequest = baseRequest.completeMultipartUpload(uploadId, completeRequest);
			CompleteMultipartUploadResult result = restClient.post()
				.uri(completeUploadRequest.uri())
				.headers(completeUploadRequest.headers())
				.body(completeRequest)
				.retrieve()
				.body(CompleteMultipartUploadResult.class);

			// Verify the upload
			assertThat(result).isNotNull();
			assertThat(result.bucket()).isEqualTo(bucketName);
			assertThat(result.key()).isEqualTo(objectKey);
			assertThat(result.etag()).isNotNull();

			// Verify the uploaded content
			byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
			assertThat(downloadedData).isEqualTo(data);

			// Verify data integrity with MD5 hash
			String originalMd5 = calculateMd5(data);
			String downloadedMd5 = calculateMd5(downloadedData);
			assertThat(downloadedMd5).isEqualTo(originalMd5);

		}
		catch (Exception e) {
			// Abort multipart upload on error
			S3Request abortRequest = baseRequest.abortMultipartUpload("dummy-upload-id");
			restClient.delete().uri(abortRequest.uri()).headers(abortRequest.headers()).retrieve().toBodilessEntity();
			throw new RuntimeException("Low level multipart upload failed", e);
		}
		finally {
			// Clean up
			client.bucket(bucketName).object(objectKey).delete();
		}
	}

	@Test
	void testMultipartUploadAsync() throws Exception {
		String objectKey = "async-multipart-file.txt";

		// Create a 10MB file (2 parts of 5MB each)
		byte[] data = createTestData(10 * 1024 * 1024);

		// Track progress
		AtomicInteger completedParts = new AtomicInteger(0);
		AtomicLong totalBytesTransferred = new AtomicLong(0);
		AtomicLong uploadCompletedTotalBytes = new AtomicLong(0);

		ProgressCallback progressCallback = new ProgressCallback() {
			@Override
			public void onPartCompleted(int partNumber, long bytesTransferred) {
				completedParts.incrementAndGet();
				totalBytesTransferred.addAndGet(bytesTransferred);
			}

			@Override
			public void onUploadCompleted(long totalBytes) {
				uploadCompletedTotalBytes.set(totalBytes);
			}

			@Override
			public void onError(Exception error) {
				throw new RuntimeException("Async upload failed", error);
			}
		};

		// Perform asynchronous multipart upload
		CompletableFuture<CompleteMultipartUploadResult> future = client.bucket(bucketName)
			.object(objectKey)
			.multipartUpload()
			.partSize(DataSize.ofMegabytes(5))
			.maxConcurrentUploads(2)
			.progressCallback(progressCallback)
			.uploadAsync(data);

		// Wait for completion
		CompleteMultipartUploadResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);

		// Verify the upload
		assertThat(result).isNotNull();
		assertThat(result.bucket()).isEqualTo(bucketName);
		assertThat(result.key()).isEqualTo(objectKey);
		assertThat(result.etag()).isNotNull();

		// Verify progress tracking
		assertThat(completedParts.get()).isEqualTo(2); // 2 parts
		assertThat(totalBytesTransferred.get()).isEqualTo(data.length);
		assertThat(uploadCompletedTotalBytes.get()).isEqualTo(data.length);

		// Verify the uploaded content
		byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
		assertThat(downloadedData).isEqualTo(data);

		// Verify data integrity with MD5 hash
		String originalMd5 = calculateMd5(data);
		String downloadedMd5 = calculateMd5(downloadedData);
		assertThat(downloadedMd5).isEqualTo(originalMd5);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
	}

	@Test
	void testMultipartUploadAsyncWithCustomExecutor() throws Exception {
		String objectKey = "async-executor-multipart-file.txt";

		// Create a 8MB file
		byte[] data = createTestData(8 * 1024 * 1024);

		// Create custom executor
		ExecutorService customExecutor = java.util.concurrent.Executors.newFixedThreadPool(1);

		try {
			// Track progress
			AtomicInteger completedParts = new AtomicInteger(0);
			AtomicLong uploadCompletedTotalBytes = new AtomicLong(0);

			ProgressCallback progressCallback = new ProgressCallback() {
				@Override
				public void onPartCompleted(int partNumber, long bytesTransferred) {
					completedParts.incrementAndGet();
				}

				@Override
				public void onUploadCompleted(long totalBytes) {
					uploadCompletedTotalBytes.set(totalBytes);
				}

				@Override
				public void onError(Exception error) {
					throw new RuntimeException("Async upload with custom executor failed", error);
				}
			};

			// Perform asynchronous multipart upload with custom executor
			CompletableFuture<CompleteMultipartUploadResult> future = client.bucket(bucketName)
				.object(objectKey)
				.multipartUpload()
				.partSize(DataSize.ofMegabytes(5))
				.maxConcurrentUploads(1)
				.executor(customExecutor)
				.progressCallback(progressCallback)
				.uploadAsync(data);

			// Wait for completion
			CompleteMultipartUploadResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);

			// Verify the upload
			assertThat(result).isNotNull();
			assertThat(result.bucket()).isEqualTo(bucketName);
			assertThat(result.key()).isEqualTo(objectKey);
			assertThat(result.etag()).isNotNull();

			// Verify progress tracking
			assertThat(completedParts.get()).isEqualTo(2); // 2 parts (8MB / 5MB = 2
															// parts)
			assertThat(uploadCompletedTotalBytes.get()).isEqualTo(data.length);

			// Verify the uploaded content
			byte[] downloadedData = client.bucket(bucketName).object(objectKey).getAsBytes();
			assertThat(downloadedData).isEqualTo(data);

			// Verify data integrity with MD5 hash
			String originalMd5 = calculateMd5(data);
			String downloadedMd5 = calculateMd5(downloadedData);
			assertThat(downloadedMd5).isEqualTo(originalMd5);

		}
		finally {
			// Clean up
			client.bucket(bucketName).object(objectKey).delete();
			customExecutor.shutdown();
		}
	}

	private byte[] createTestData(int size) {
		StringBuilder sb = new StringBuilder();
		String pattern = "This is a test line for multipart upload testing. ";
		while (sb.length() < size) {
			sb.append(pattern);
		}
		return sb.substring(0, size).getBytes(StandardCharsets.UTF_8);
	}

	private String calculateMd5(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] hashBytes = md.digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : hashBytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to calculate MD5 hash", e);
		}
	}

}