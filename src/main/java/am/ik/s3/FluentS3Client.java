package am.ik.s3;

import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

/**
 * Fluent API-based S3 client providing chainable methods for S3 operations. This client
 * offers a more intuitive and modern approach compared to the deprecated S3Client. It
 * uses RestClient internally and provides type-safe operations for common S3 tasks.
 *
 * <p>
 * Example usage: <pre>{@code
 * FluentS3Client client = FluentS3Client.builder()
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
public final class FluentS3Client {

	private final S3OperationBuilder operationBuilder;

	/**
	 * Private constructor. Use builder() to create instances.
	 * @param restClient The RestClient instance to use
	 * @param configuration The S3 client configuration
	 */
	private FluentS3Client(RestClient restClient, S3ClientConfiguration configuration) {
		this.operationBuilder = new S3OperationBuilder(restClient, configuration);
	}

	/**
	 * Creates a new FluentS3Client builder.
	 * @return A new FluentS3ClientBuilder instance
	 */
	public static FluentS3ClientBuilder builder() {
		return new FluentS3ClientBuilder();
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
	 * Builder class for creating FluentS3Client instances.
	 */
	public static final class FluentS3ClientBuilder {

		private URI endpoint;

		private String region;

		private String accessKeyId;

		private String secretAccessKey;

		private RestClient restClient;

		private FluentS3ClientBuilder() {
		}

		/**
		 * Sets the S3 endpoint URI.
		 * @param endpoint The S3 endpoint URI
		 * @return This builder instance
		 */
		public FluentS3ClientBuilder endpoint(URI endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		/**
		 * Sets the S3 endpoint URL.
		 * @param endpoint The S3 endpoint URL
		 * @return This builder instance
		 */
		public FluentS3ClientBuilder endpoint(String endpoint) {
			return endpoint(URI.create(endpoint));
		}

		/**
		 * Sets the AWS region.
		 * @param region The AWS region
		 * @return This builder instance
		 */
		public FluentS3ClientBuilder region(String region) {
			this.region = region;
			return this;
		}

		/**
		 * Sets the AWS credentials.
		 * @param accessKeyId The AWS access key ID
		 * @param secretAccessKey The AWS secret access key
		 * @return This builder instance
		 */
		public FluentS3ClientBuilder credentials(String accessKeyId, String secretAccessKey) {
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
		public FluentS3ClientBuilder restClient(RestClient restClient) {
			this.restClient = restClient;
			return this;
		}

		/**
		 * Builds the FluentS3Client instance.
		 * @return A new FluentS3Client instance
		 * @throws IllegalArgumentException if any required configuration is missing
		 */
		public FluentS3Client build() {
			S3ClientConfiguration configuration = new S3ClientConfiguration(endpoint, region, accessKeyId,
					secretAccessKey);
			configuration.validate();

			RestClient clientToUse = restClient != null ? restClient : createDefaultRestClient();

			return new FluentS3Client(clientToUse, configuration);
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