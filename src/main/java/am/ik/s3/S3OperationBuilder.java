package am.ik.s3;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import static am.ik.s3.S3RequestBuilder.s3Request;

/**
 * Builder class for S3 operations providing fluent API for bucket and object operations.
 * This class encapsulates the common patterns for building S3 requests and executing
 * them.
 *
 * @since 0.3.0
 */
public class S3OperationBuilder {

	private final RestClient restClient;

	private final S3ClientConfiguration configuration;

	/**
	 * Constructor for S3OperationBuilder.
	 * @param restClient The RestClient instance to use for HTTP operations
	 * @param configuration The S3 client configuration
	 */
	public S3OperationBuilder(RestClient restClient, S3ClientConfiguration configuration) {
		this.restClient = restClient;
		this.configuration = configuration;
	}

	/**
	 * Creates a bucket operation builder.
	 * @param bucketName The name of the bucket
	 * @return A new BucketOperationBuilder instance
	 */
	public BucketOperationBuilder bucket(String bucketName) {
		return new BucketOperationBuilder(bucketName);
	}

	/**
	 * Lists all buckets.
	 * @return ListBucketsResult containing all buckets
	 */
	public ListBucketsResult listBuckets() {
		S3Request request = s3Request().endpoint(configuration.endpoint())
			.region(configuration.region())
			.accessKeyId(configuration.accessKeyId())
			.secretAccessKey(configuration.secretAccessKey())
			.method(HttpMethod.GET)
			.path(Function.identity())
			.build();

		return restClient.get().uri(request.uri()).headers(request.headers()).retrieve().body(ListBucketsResult.class);
	}

	/**
	 * Builder class for bucket-specific operations.
	 */
	public class BucketOperationBuilder {

		private final String bucketName;

		private BucketOperationBuilder(String bucketName) {
			this.bucketName = bucketName;
		}

		/**
		 * Creates the bucket.
		 * @return true if the bucket was created successfully
		 */
		public boolean create() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucketName))
				.build();

			restClient.put().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();

			return true;
		}

		/**
		 * Deletes the bucket.
		 * @return true if the bucket was deleted successfully
		 */
		public boolean delete() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.DELETE)
				.path(b -> b.bucket(bucketName))
				.build();

			restClient.delete().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();

			return true;
		}

		/**
		 * Lists objects in the bucket.
		 * @return ListBucketResult containing the objects in the bucket
		 */
		public ListBucketResult listObjects() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.GET)
				.path(b -> b.bucket(bucketName))
				.build();

			return restClient.get()
				.uri(request.uri())
				.headers(request.headers())
				.retrieve()
				.body(ListBucketResult.class);
		}

		/**
		 * Creates an object operation builder for a specific object key.
		 * @param objectKey The object key
		 * @return A new ObjectOperationBuilder instance
		 */
		public ObjectOperationBuilder object(String objectKey) {
			return new ObjectOperationBuilder(bucketName, objectKey);
		}

	}

	/**
	 * Builder class for object-specific operations.
	 */
	public class ObjectOperationBuilder {

		private final String bucketName;

		private final String objectKey;

		private ObjectOperationBuilder(String bucketName, String objectKey) {
			this.bucketName = bucketName;
			this.objectKey = objectKey;
		}

		/**
		 * Puts (uploads) an object with string content.
		 * @param content The content to upload
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(String content) {
			return put(content, MediaType.TEXT_PLAIN);
		}

		/**
		 * Puts (uploads) an object with string content and specified MIME type.
		 * @param content The content to upload
		 * @param mimeType The MIME type of the content
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(String content, MediaType mediaType) {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.of(content, mediaType))
				.build();

			restClient.put().uri(request.uri()).headers(request.headers()).body(content).retrieve().toBodilessEntity();

			return true;
		}

		/**
		 * Puts (uploads) an object with byte array content.
		 * @param content The content to upload
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(byte[] content) {
			return put(content, MediaType.APPLICATION_OCTET_STREAM);
		}

		/**
		 * Puts (uploads) an object with byte array content and specified MIME type.
		 * @param content The content to upload
		 * @param mimeType The MIME type of the content
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(byte[] content, MediaType mediaType) {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.of(content, mediaType))
				.build();

			restClient.put().uri(request.uri()).headers(request.headers()).body(content).retrieve().toBodilessEntity();

			return true;
		}

		/**
		 * Gets (downloads) an object as a string.
		 * @return The object content as a string
		 */
		public String get() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.GET)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();

			return restClient.get().uri(request.uri()).headers(request.headers()).retrieve().body(String.class);
		}

		/**
		 * Gets (downloads) an object as a byte array.
		 * @return The object content as a byte array
		 */
		public byte[] getAsBytes() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.GET)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();

			return restClient.get().uri(request.uri()).headers(request.headers()).retrieve().body(byte[].class);
		}

		/**
		 * Deletes the object.
		 * @return true if the object was deleted successfully
		 */
		public boolean delete() {
			S3Request request = s3Request().endpoint(configuration.endpoint())
				.region(configuration.region())
				.accessKeyId(configuration.accessKeyId())
				.secretAccessKey(configuration.secretAccessKey())
				.method(HttpMethod.DELETE)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();

			restClient.delete().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();

			return true;
		}

	}

}