package am.ik.s3;

import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

/**
 * S3 client providing chainable methods for S3 operations. This client uses RestClient
 * internally and provides type-safe operations for common S3 tasks.
 *
 * <p>
 * Example usage: <pre>{@code
 * S3Client client = S3Client.builder()
 *     .endpoint("https://s3.amazonaws.com")
 *     .region("us-east-1")
 *     .credentials("accessKey", "secretKey")
 *     .build();
 *
 * // Create bucket
 * client.bucket("my-bucket").create();
 *
 * // Upload object
 * client.bucket("my-bucket").object("file.txt").put("content");
 *
 * // Download object
 * String content = client.bucket("my-bucket").object("file.txt").get();
 * }</pre>
 *
 * @since 0.3.0
 */
public final class S3Client {

	private final S3OperationBuilder operationBuilder;

	/**
	 * Private constructor. Use builder() to create instances.
	 * @param restClient The RestClient instance to use
	 * @param endpoint The S3 endpoint URI
	 * @param region The AWS region
	 * @param accessKeyId The AWS access key ID
	 * @param secretAccessKey The AWS secret access key
	 */
	private S3Client(RestClient restClient, URI endpoint, String region, String accessKeyId, String secretAccessKey) {
		this.operationBuilder = new S3OperationBuilder(restClient, endpoint, region, accessKeyId, secretAccessKey);
	}

	/**
	 * Creates a new S3Client builder.
	 * @return A new S3ClientBuilder instance
	 */
	public static S3ClientBuilder builder() {
		return new S3ClientBuilder();
	}

	/**
	 * Creates a bucket operation builder.
	 * @param bucketName The name of the bucket
	 * @return A BucketOperationBuilder for the specified bucket
	 */
	public S3OperationBuilder.BucketOperationBuilder bucket(String bucketName) {
		return operationBuilder.bucket(bucketName);
	}

	/**
	 * Lists all buckets.
	 * @return ListBucketsResult containing all buckets
	 */
	public ListBucketsResult listBuckets() {
		return operationBuilder.listBuckets();
	}

	/**
	 * Builder class for creating S3Client instances.
	 */
	public static final class S3ClientBuilder {

		private URI endpoint;

		private String region;

		private String accessKeyId;

		private String secretAccessKey;

		private RestClient restClient;

		private S3ClientBuilder() {
		}

		/**
		 * Sets the S3 endpoint URI.
		 * @param endpoint The S3 endpoint URI
		 * @return This builder instance
		 */
		public S3ClientBuilder endpoint(URI endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		/**
		 * Sets the S3 endpoint URL.
		 * @param endpoint The S3 endpoint URL
		 * @return This builder instance
		 */
		public S3ClientBuilder endpoint(String endpoint) {
			return endpoint(URI.create(endpoint));
		}

		/**
		 * Sets the AWS region.
		 * @param region The AWS region
		 * @return This builder instance
		 */
		public S3ClientBuilder region(String region) {
			this.region = region;
			return this;
		}

		/**
		 * Sets the AWS credentials.
		 * @param accessKeyId The AWS access key ID
		 * @param secretAccessKey The AWS secret access key
		 * @return This builder instance
		 */
		public S3ClientBuilder credentials(String accessKeyId, String secretAccessKey) {
			this.accessKeyId = accessKeyId;
			this.secretAccessKey = secretAccessKey;
			return this;
		}

		/**
		 * Sets the RestClient to use for HTTP operations. If not specified, a default
		 * RestClient will be created.
		 * @param restClient The RestClient instance
		 * @return This builder instance
		 */
		public S3ClientBuilder restClient(RestClient restClient) {
			this.restClient = restClient;
			return this;
		}

		/**
		 * Builds the S3Client instance.
		 * @return A new S3Client instance
		 * @throws IllegalArgumentException if any required configuration is missing
		 */
		public S3Client build() {
			validateConfiguration();

			RestClient clientToUse = restClient != null ? restClient : createDefaultRestClient();

			return new S3Client(clientToUse, endpoint, region, accessKeyId, secretAccessKey);
		}

		/**
		 * Validates the configuration parameters.
		 * @throws IllegalArgumentException if any required parameter is null or empty
		 */
		private void validateConfiguration() {
			if (endpoint == null) {
				throw new IllegalArgumentException("Endpoint must not be null");
			}
			if (region == null || region.isBlank()) {
				throw new IllegalArgumentException("Region must not be null or empty");
			}
			if (accessKeyId == null || accessKeyId.isBlank()) {
				throw new IllegalArgumentException("Access key ID must not be null or empty");
			}
			if (secretAccessKey == null || secretAccessKey.isBlank()) {
				throw new IllegalArgumentException("Secret access key must not be null or empty");
			}
		}

		/**
		 * Creates a default RestClient configured with XML message converter.
		 * @return A configured RestClient instance
		 */
		private static RestClient createDefaultRestClient() {
			return RestClient.builder()
				.messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
				.build();
		}

	}

}