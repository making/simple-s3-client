package am.ik.s3;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

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

	private final URI endpoint;

	private final String region;

	private final String accessKeyId;

	private final String secretAccessKey;

	/**
	 * Constructor for S3OperationBuilder.
	 * @param restClient The RestClient instance to use for HTTP operations
	 * @param endpoint The S3 endpoint URI
	 * @param region The AWS region
	 * @param accessKeyId The AWS access key ID
	 * @param secretAccessKey The AWS secret access key
	 */
	public S3OperationBuilder(RestClient restClient, URI endpoint, String region, String accessKeyId,
			String secretAccessKey) {
		this.restClient = restClient;
		this.endpoint = endpoint;
		this.region = region;
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
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
		S3Request request = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
		 * @param mediaType The media type of the content
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(String content, MediaType mediaType) {
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
		 * @param mediaType The media type of the content
		 * @return true if the object was uploaded successfully
		 */
		public boolean put(byte[] content, MediaType mediaType) {
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
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
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
				.method(HttpMethod.DELETE)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();
			restClient.delete().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();
			return true;
		}

		/**
		 * Generates a presigned URL for this object with the specified HTTP method,
		 * expiration, and additional headers.
		 * @param method the HTTP method (GET, PUT, or DELETE)
		 * @param expiration the duration until the URL expires
		 * @param additionalHeaders additional headers to include in the presigned URL
		 * @return a PresignedUrl containing the URL and expiration information
		 * @throws IllegalArgumentException if the method is not GET, PUT, or DELETE
		 * @since 0.3.0
		 */
		public PresignedUrl presignedUrl(HttpMethod method, java.time.Duration expiration,
				Map<String, String> additionalHeaders) {
			if (!(method == HttpMethod.GET || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
				throw new IllegalArgumentException("Method not supported: " + method);
			}
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
				.method(method)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();
			return request.presignedUrl(expiration, additionalHeaders);
		}

		/**
		 * Generates a presigned URL for this object with the specified HTTP method and
		 * expiration.
		 * @param method the HTTP method (GET, PUT, or DELETE)
		 * @param expiration the duration until the URL expires
		 * @return a PresignedUrl containing the URL and expiration information
		 * @throws IllegalArgumentException if the method is not GET, PUT, or DELETE
		 * @since 0.3.0
		 */
		public PresignedUrl presignedUrl(HttpMethod method, java.time.Duration expiration) {
			return presignedUrl(method, expiration, Map.of());
		}

		/**
		 * Generates a presigned URL for this object with the specified HTTP method and a
		 * default expiration of 1 hour.
		 * @param method the HTTP method (GET, PUT, or DELETE)
		 * @return a PresignedUrl containing the URL and expiration information
		 * @throws IllegalArgumentException if the method is not GET, PUT, or DELETE
		 * @since 0.3.0
		 */
		public PresignedUrl presignedUrl(HttpMethod method) {
			return presignedUrl(method, java.time.Duration.ofHours(1));
		}

		/**
		 * Creates a presigned POST form generator for this object with a default
		 * expiration of 1 hour.
		 * @return a new PresignedPostForm.Generator instance
		 * @since 0.3.0
		 */
		public PresignedPostForm.Generator presignedPostForm() {
			return presignedPostForm(java.time.Duration.ofHours(1));
		}

		/**
		 * Creates a presigned POST form generator for this object.
		 * @param expiration the duration until the URL expires
		 * @return a new PresignedPostForm.Builder instance
		 * @since 0.3.0
		 */
		public PresignedPostForm.Generator presignedPostForm(java.time.Duration expiration) {
			S3Request request = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
				.method(org.springframework.http.HttpMethod.POST)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();
			return request.presignedPostForm(expiration);
		}

		/**
		 * Creates a multipart upload builder for this object.
		 * @return a new MultipartUploadBuilder instance
		 * @since 0.3.0
		 */
		public MultipartUploadBuilder multipartUpload() {
			return new MultipartUploadBuilder(bucketName, objectKey);
		}

	}

	/**
	 * Builder class for multipart upload operations.
	 */
	public class MultipartUploadBuilder {

		private final String bucketName;

		private final String objectKey;

		private MultipartUploadConfiguration configuration = MultipartUploadConfiguration.defaultConfiguration();

		private ProgressCallback progressCallback = ProgressCallback.noOp();

		private MultipartUploadBuilder(String bucketName, String objectKey) {
			this.bucketName = bucketName;
			this.objectKey = objectKey;
		}

		/**
		 * Sets the part size for multipart upload.
		 * @param partSize the part size
		 * @return this builder
		 */
		public MultipartUploadBuilder partSize(org.springframework.util.unit.DataSize partSize) {
			this.configuration = MultipartUploadConfiguration.builder()
				.partSize(partSize)
				.maxConcurrentUploads(configuration.maxConcurrentUploads())
				.enableProgressTracking(configuration.enableProgressTracking())
				.executor(configuration.executor())
				.build();
			return this;
		}

		/**
		 * Sets the maximum number of concurrent uploads.
		 * @param maxConcurrentUploads the maximum concurrent uploads
		 * @return this builder
		 */
		public MultipartUploadBuilder maxConcurrentUploads(int maxConcurrentUploads) {
			this.configuration = MultipartUploadConfiguration.builder()
				.partSize(configuration.partSize())
				.maxConcurrentUploads(maxConcurrentUploads)
				.enableProgressTracking(configuration.enableProgressTracking())
				.executor(configuration.executor())
				.build();
			return this;
		}

		/**
		 * Sets the progress callback for tracking upload progress.
		 * @param progressCallback the progress callback
		 * @return this builder
		 */
		public MultipartUploadBuilder progressCallback(ProgressCallback progressCallback) {
			this.progressCallback = progressCallback != null ? progressCallback : ProgressCallback.noOp();
			return this;
		}

		/**
		 * Sets the executor for asynchronous operations.
		 * @param executor the executor to use for async operations
		 * @return this builder
		 */
		public MultipartUploadBuilder executor(java.util.concurrent.Executor executor) {
			this.configuration = MultipartUploadConfiguration.builder()
				.partSize(configuration.partSize())
				.maxConcurrentUploads(configuration.maxConcurrentUploads())
				.enableProgressTracking(configuration.enableProgressTracking())
				.executor(executor)
				.build();
			return this;
		}

		/**
		 * Sets the multipart upload configuration.
		 * @param configuration the configuration
		 * @return this builder
		 */
		public MultipartUploadBuilder configuration(MultipartUploadConfiguration configuration) {
			this.configuration = configuration != null ? configuration
					: MultipartUploadConfiguration.defaultConfiguration();
			return this;
		}

		/**
		 * Uploads data using multipart upload.
		 * @param data the data to upload
		 * @return the result of the completed multipart upload
		 */
		public CompleteMultipartUploadResult upload(byte[] data) {
			return createMultipartUpload().upload(data);
		}

		/**
		 * Uploads data from an input stream using multipart upload.
		 * @param inputStream the input stream to read data from
		 * @param contentLength the total length of the data
		 * @return the result of the completed multipart upload
		 */
		public CompleteMultipartUploadResult upload(java.io.InputStream inputStream, long contentLength) {
			return createMultipartUpload().upload(inputStream, contentLength);
		}

		/**
		 * Uploads data using multipart upload asynchronously.
		 * @param data the data to upload
		 * @return a CompletableFuture that will complete with the upload result
		 */
		public java.util.concurrent.CompletableFuture<CompleteMultipartUploadResult> uploadAsync(byte[] data) {
			return createMultipartUpload().uploadAsync(data);
		}

		/**
		 * Uploads data from an input stream using multipart upload asynchronously.
		 * @param inputStream the input stream to read data from
		 * @param contentLength the total length of the data
		 * @return a CompletableFuture that will complete with the upload result
		 */
		public java.util.concurrent.CompletableFuture<CompleteMultipartUploadResult> uploadAsync(
				java.io.InputStream inputStream, long contentLength) {
			return createMultipartUpload().uploadAsync(inputStream, contentLength);
		}

		private MultipartUpload createMultipartUpload() {
			S3Request baseRequest = s3Request().endpoint(endpoint)
				.region(region)
				.accessKeyId(accessKeyId)
				.secretAccessKey(secretAccessKey)
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucketName).key(objectKey))
				.build();

			return new MultipartUpload(restClient, baseRequest, configuration, progressCallback);
		}

	}

}