package am.ik.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Integration tests for S3Client using LocalStack.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ClientIntegrationTest {

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.0.0"))
		.withServices(S3);

	private S3Client client;

	@BeforeEach
	void setUp() {
		RestClient restClient = RestClient.builder()
			.messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
			.build();

		client = S3Client.builder()
			.endpoint(localstack.getEndpointOverride(S3).toString())
			.region(localstack.getRegion())
			.credentials(localstack.getAccessKey(), localstack.getSecretKey())
			.restClient(restClient)
			.build();
	}

	@Test
	void shouldListBucketsInitiallyEmpty() {
		ListBucketsResult result = client.listBuckets();

		assertThat(result).isNotNull();
		assertThat(result.buckets()).isNullOrEmpty();
	}

	@Test
	void shouldCreateAndDeleteBucket() {
		String bucketName = "test-bucket";

		// Create bucket
		client.bucket(bucketName).create();

		// Verify bucket exists
		ListBucketsResult result = client.listBuckets();
		assertThat(result.buckets()).hasSize(1);
		assertThat(result.buckets().get(0).name()).isEqualTo(bucketName);

		// Delete bucket
		client.bucket(bucketName).delete();

		// Verify bucket is deleted
		result = client.listBuckets();
		assertThat(result.buckets()).isNullOrEmpty();
	}

	@Test
	void shouldPutAndGetStringObject() {
		String bucketName = "test-bucket";
		String objectKey = "test-object.txt";
		String content = "Hello, World!";

		// Create bucket
		client.bucket(bucketName).create();

		// Put object
		client.bucket(bucketName).object(objectKey).put(content);

		// Get object
		String retrievedContent = client.bucket(bucketName).object(objectKey).getAsString();
		assertThat(retrievedContent).isEqualTo(content);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
		client.bucket(bucketName).delete();
	}

	@Test
	void shouldPutAndGetByteArrayObject() {
		String bucketName = "test-bucket";
		String objectKey = "test-binary.bin";
		byte[] content = "Binary content".getBytes();

		// Create bucket
		client.bucket(bucketName).create();

		// Put object
		client.bucket(bucketName).object(objectKey).put(content);

		// Get object as bytes
		byte[] retrievedContent = client.bucket(bucketName).object(objectKey).getAsBytes();
		assertThat(retrievedContent).isEqualTo(content);

		// Clean up
		client.bucket(bucketName).object(objectKey).delete();
		client.bucket(bucketName).delete();
	}

	@Test
	void shouldListObjectsInBucket() {
		String bucketName = "test-bucket";
		String objectKey1 = "file1.txt";
		String objectKey2 = "file2.txt";

		// Create bucket
		client.bucket(bucketName).create();

		// Put objects
		client.bucket(bucketName).object(objectKey1).put("content1");
		client.bucket(bucketName).object(objectKey2).put("content2");

		// List objects
		ListBucketResult result = client.bucket(bucketName).listObjects();
		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(2);

		List<String> objectKeys = result.contents().stream().map(Content::key).sorted().toList();

		assertThat(objectKeys).containsExactly(objectKey1, objectKey2);

		// Clean up
		client.bucket(bucketName).object(objectKey1).delete();
		client.bucket(bucketName).object(objectKey2).delete();
		client.bucket(bucketName).delete();
	}

	@Test
	void shouldDeleteObject() {
		String bucketName = "test-bucket";
		String objectKey = "test-object.txt";

		// Create bucket and put object
		client.bucket(bucketName).create();
		client.bucket(bucketName).object(objectKey).put("content");

		// Verify object exists
		ListBucketResult result = client.bucket(bucketName).listObjects();
		assertThat(result.contents()).hasSize(1);

		// Delete object
		client.bucket(bucketName).object(objectKey).delete();

		// Verify object is deleted
		result = client.bucket(bucketName).listObjects();
		assertThat(result).isNotNull();
		assertThat(result.contents()).isNullOrEmpty();

		// Clean up
		client.bucket(bucketName).delete();
	}

	@Test
	void shouldSupportFluentChaining() {
		String bucketName = "fluent-test-bucket";
		String objectKey = "fluent-test.txt";
		String content = "Fluent API Test";

		// Demonstrate fluent API chaining
		client.bucket(bucketName).create();
		client.bucket(bucketName).object(objectKey).put(content);

		String retrievedContent = client.bucket(bucketName).object(objectKey).getAsString();
		assertThat(retrievedContent).isEqualTo(content);

		// Clean up with fluent chaining
		client.bucket(bucketName).object(objectKey).delete();
		client.bucket(bucketName).delete();
	}

}