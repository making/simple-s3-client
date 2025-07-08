package am.ik.s3;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FluentS3Client.
 */
class FluentS3ClientTest {

	@Test
	void builderShouldCreateValidClient() {
		FluentS3Client client = FluentS3Client.builder()
			.endpoint("https://s3.amazonaws.com")
			.region("us-east-1")
			.credentials("accessKey", "secretKey")
			.build();

		assertThat(client).isNotNull();
	}

	@Test
	void builderShouldAcceptCustomRestClient() {
		RestClient customRestClient = RestClient.create();

		FluentS3Client client = FluentS3Client.builder()
			.endpoint("https://s3.amazonaws.com")
			.region("us-east-1")
			.credentials("accessKey", "secretKey")
			.restClient(customRestClient)
			.build();

		assertThat(client).isNotNull();
	}

	@Test
	void builderShouldThrowExceptionForMissingEndpoint() {
		assertThatThrownBy(
				() -> FluentS3Client.builder().region("us-east-1").credentials("accessKey", "secretKey").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Endpoint must not be null");
	}

	@Test
	void builderShouldThrowExceptionForMissingRegion() {
		assertThatThrownBy(() -> FluentS3Client.builder()
			.endpoint("https://s3.amazonaws.com")
			.credentials("accessKey", "secretKey")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("Region must not be null or empty");
	}

	@Test
	void builderShouldThrowExceptionForMissingCredentials() {
		assertThatThrownBy(
				() -> FluentS3Client.builder().endpoint("https://s3.amazonaws.com").region("us-east-1").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Access key ID must not be null or empty");
	}

	@Test
	void bucketShouldReturnBucketOperationBuilder() {
		FluentS3Client client = FluentS3Client.builder()
			.endpoint("https://s3.amazonaws.com")
			.region("us-east-1")
			.credentials("accessKey", "secretKey")
			.build();

		S3OperationBuilder.BucketOperationBuilder bucketBuilder = client.bucket("test-bucket");

		assertThat(bucketBuilder).isNotNull();
	}

	@Test
	void bucketOperationBuilderShouldReturnObjectOperationBuilder() {
		FluentS3Client client = FluentS3Client.builder()
			.endpoint("https://s3.amazonaws.com")
			.region("us-east-1")
			.credentials("accessKey", "secretKey")
			.build();

		S3OperationBuilder.ObjectOperationBuilder objectBuilder = client.bucket("test-bucket").object("test-key");

		assertThat(objectBuilder).isNotNull();
	}

}