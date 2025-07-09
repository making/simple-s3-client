package am.ik.s3;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.core.io.Resource;
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
	 * Creates a base S3Request builder with common configuration.
	 * @param method The HTTP method
	 * @param pathFunction The path function for bucket/key configuration
	 * @return A configured S3Request builder
	 */
	private S3RequestBuilders.Optionals prepareRequestBuilder(HttpMethod method,
			Function<S3PathBuilder, S3PathBuilder> pathFunction) {
		return s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(method)
			.path(pathFunction);
	}

	/**
	 * Prepares a base S3Request with common configuration.
	 * @param method The HTTP method
	 * @param pathFunction The path function for bucket/key configuration
	 * @return A configured S3Request
	 */
	private S3Request prepareRequest(HttpMethod method, Function<S3PathBuilder, S3PathBuilder> pathFunction) {
		return prepareRequestBuilder(method, pathFunction).build();
	}

	/**
	 * Executes a GET request and returns the response body.
	 * @param request The S3Request to execute
	 * @param responseType The expected response type
	 * @return The response body
	 */
	private <T> T executeGetRequest(S3Request request, Class<T> responseType) {
		return restClient.get().uri(request.uri()).headers(request.headers()).retrieve().body(responseType);
	}

	/**
	 * Executes a PUT request without a body.
	 * @param request The S3Request to execute
	 */
	private void executePutRequest(S3Request request) {
		restClient.put().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();
	}

	/**
	 * Executes a PUT request with a body.
	 * @param request The S3Request to execute
	 * @param body The request body
	 */
	private void executePutRequest(S3Request request, Object body) {
		restClient.put().uri(request.uri()).headers(request.headers()).body(body).retrieve().toBodilessEntity();
	}

	/**
	 * Executes a DELETE request.
	 * @param request The S3Request to execute
	 */
	private void executeDeleteRequest(S3Request request) {
		restClient.delete().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();
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
		S3Request request = prepareRequest(HttpMethod.GET, Function.identity());
		return executeGetRequest(request, ListBucketsResult.class);
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
		 */
		public void create() {
			S3Request request = prepareRequest(HttpMethod.PUT, b -> b.bucket(bucketName));
			executePutRequest(request);
		}

		/**
		 * Deletes the bucket.
		 */
		public void delete() {
			S3Request request = prepareRequest(HttpMethod.DELETE, b -> b.bucket(bucketName));
			executeDeleteRequest(request);
		}

		/**
		 * Lists objects in the bucket.
		 * @return ListBucketResult containing the objects in the bucket
		 */
		public ListBucketResult listObjects() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName));
			return executeGetRequest(request, ListBucketResult.class);
		}

		/**
		 * Lists object versions in the bucket.
		 * @return ListVersionsResult containing the object versions in the bucket
		 * @since 0.3.0
		 */
		public ListVersionsResult listVersions() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName)).listVersions();
			return executeGetRequest(request, ListVersionsResult.class);
		}

		/**
		 * Lists object versions in the bucket with parameters.
		 * @param prefix the prefix to filter objects
		 * @param keyMarker the key marker for pagination
		 * @param versionIdMarker the version ID marker for pagination
		 * @param maxKeys the maximum number of keys to return
		 * @return ListVersionsResult containing the object versions in the bucket
		 * @since 0.3.0
		 */
		public ListVersionsResult listVersions(String prefix, String keyMarker, String versionIdMarker,
				Integer maxKeys) {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName)).listVersions(prefix,
					keyMarker, versionIdMarker, maxKeys);
			return executeGetRequest(request, ListVersionsResult.class);
		}

		/**
		 * Enables versioning for the bucket.
		 * @since 0.3.0
		 */
		public void enableVersioning() {
			setVersioningConfiguration(VersioningConfiguration.enabled());
		}

		/**
		 * Suspends versioning for the bucket.
		 * @since 0.3.0
		 */
		public void suspendVersioning() {
			setVersioningConfiguration(VersioningConfiguration.suspended());
		}

		/**
		 * Sets the versioning configuration for the bucket.
		 * @param versioningConfiguration the versioning configuration to set
		 * @since 0.3.0
		 */
		public void setVersioningConfiguration(VersioningConfiguration versioningConfiguration) {
			String versioningConfigurationXml = versioningConfiguration.toXml();
			S3Request request = prepareRequestBuilder(HttpMethod.PUT, b -> b.bucket(bucketName))
				.canonicalQueryString("versioning=")
				.content(S3Content.of(versioningConfigurationXml, MediaType.APPLICATION_XML))
				.build();
			executePutRequest(request, versioningConfigurationXml);
		}

		/**
		 * Gets the versioning configuration for the bucket.
		 * @return the current versioning configuration
		 * @since 0.3.0
		 */
		public VersioningConfiguration getVersioningConfiguration() {
			S3Request request = prepareRequestBuilder(HttpMethod.GET, b -> b.bucket(bucketName))
				.canonicalQueryString("versioning=")
				.build();
			return executeGetRequest(request, VersioningConfiguration.class);
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
		 */
		public void put(String content) {
			put(content, MediaType.TEXT_PLAIN);
		}

		/**
		 * Puts (uploads) an object with string content and specified MIME type.
		 * @param content The content to upload
		 * @param mediaType The media type of the content
		 */
		public void put(String content, MediaType mediaType) {
			S3Request request = prepareRequestBuilder(HttpMethod.PUT, b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.of(content, mediaType))
				.build();
			executePutRequest(request, content);
		}

		/**
		 * Puts (uploads) an object with byte array content.
		 * @param content The content to upload
		 */
		public void put(byte[] content) {
			put(content, MediaType.APPLICATION_OCTET_STREAM);
		}

		/**
		 * Puts (uploads) an object with byte array content and specified MIME type.
		 * @param content The content to upload
		 * @param mediaType The media type of the content
		 */
		public void put(byte[] content, MediaType mediaType) {
			S3Request request = prepareRequestBuilder(HttpMethod.PUT, b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.of(content, mediaType))
				.build();
			executePutRequest(request, content);
		}

		/**
		 * Puts (uploads) an object with Resource content for streaming uploads.
		 * @param resource The Spring Resource to upload
		 * @since 0.3.0
		 */
		public void putResource(Resource resource) {
			putResource(resource, MediaType.APPLICATION_OCTET_STREAM);
		}

		/**
		 * Puts (uploads) an object with Resource content and specified MIME type for
		 * streaming uploads.
		 * @param resource The Spring Resource to upload
		 * @param mediaType The media type of the content
		 * @since 0.3.0
		 */
		public void putResource(Resource resource, MediaType mediaType) {
			S3Request request = prepareRequestBuilder(HttpMethod.PUT, b -> b.bucket(bucketName).key(objectKey))
				.content(S3Content.ofResource(resource, mediaType))
				.build();
			executePutRequest(request, resource);
		}

		/**
		 * Gets (downloads) an object as a string.
		 * @return The object content as a string
		 */
		public String getAsString() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName).key(objectKey));
			return executeGetRequest(request, String.class);
		}

		/**
		 * Gets (downloads) an object as a byte array.
		 * @return The object content as a byte array
		 */
		public byte[] getAsBytes() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName).key(objectKey));
			return executeGetRequest(request, byte[].class);
		}

		/**
		 * Gets (downloads) an object as a Spring Resource for streaming.
		 * @return The object content as a Spring Resource
		 * @since 0.3.0
		 */
		public Resource getAsResource() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName).key(objectKey));
			return executeGetRequest(request, Resource.class);
		}

		/**
		 * Deletes the object.
		 */
		public void delete() {
			S3Request request = prepareRequest(HttpMethod.DELETE, b -> b.bucket(bucketName).key(objectKey));
			executeDeleteRequest(request);
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
			S3Request request = prepareRequest(method, b -> b.bucket(bucketName).key(objectKey));
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
			S3Request request = prepareRequest(HttpMethod.POST, b -> b.bucket(bucketName).key(objectKey));
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

		/**
		 * Creates a range request builder for partial content retrieval.
		 * @param rangeStart the starting byte position (inclusive)
		 * @param rangeEnd the ending byte position (inclusive)
		 * @return a new RangeRequestBuilder instance
		 * @since 0.3.0
		 */
		public RangeRequestBuilder range(long rangeStart, long rangeEnd) {
			return new RangeRequestBuilder(bucketName, objectKey, rangeStart, rangeEnd);
		}

		/**
		 * Creates a range request builder for partial content retrieval from a starting
		 * position.
		 * @param rangeStart the starting byte position (inclusive)
		 * @return a new RangeRequestBuilder instance
		 * @since 0.3.0
		 */
		public RangeRequestBuilder rangeFrom(long rangeStart) {
			return new RangeRequestBuilder(bucketName, objectKey, rangeStart, null);
		}

		/**
		 * Creates a version operation builder for a specific version of this object.
		 * @param versionId the version ID of the object
		 * @return a new VersionedObjectOperationBuilder instance
		 * @since 0.3.0
		 */
		public VersionedObjectOperationBuilder version(String versionId) {
			return new VersionedObjectOperationBuilder(bucketName, objectKey, versionId);
		}

		/**
		 * Lists all versions of this object.
		 * @return ListVersionsResult containing all versions of this object
		 * @since 0.3.0
		 */
		public ListVersionsResult listVersions() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName)).listVersions(objectKey, null,
					null, null);
			return executeGetRequest(request, ListVersionsResult.class);
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
			this.configuration = updateConfiguration(builder -> builder.partSize(partSize));
			return this;
		}

		/**
		 * Sets the maximum number of concurrent uploads.
		 * @param maxConcurrentUploads the maximum concurrent uploads
		 * @return this builder
		 */
		public MultipartUploadBuilder maxConcurrentUploads(int maxConcurrentUploads) {
			this.configuration = updateConfiguration(builder -> builder.maxConcurrentUploads(maxConcurrentUploads));
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
			this.configuration = updateConfiguration(builder -> builder.executor(executor));
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
		 * Updates the current configuration by applying the given function to a builder.
		 * @param builderFunction the function to apply to the configuration builder
		 * @return the updated configuration
		 */
		private MultipartUploadConfiguration updateConfiguration(
				Function<MultipartUploadConfiguration.Builder, MultipartUploadConfiguration.Builder> builderFunction) {
			return builderFunction
				.apply(MultipartUploadConfiguration.builder()
					.partSize(configuration.partSize())
					.maxConcurrentUploads(configuration.maxConcurrentUploads())
					.enableProgressTracking(configuration.enableProgressTracking())
					.executor(configuration.executor()))
				.build();
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
		 * Uploads data from a Spring Resource using multipart upload.
		 * @param resource the Spring Resource to read data from
		 * @return the result of the completed multipart upload
		 */
		public CompleteMultipartUploadResult upload(Resource resource) {
			return createMultipartUpload().upload(resource);
		}

		/**
		 * Uploads data using multipart upload asynchronously.
		 * @param data the data to upload
		 * @return a CompletableFuture that will complete with the upload result
		 */
		public CompletableFuture<CompleteMultipartUploadResult> uploadAsync(byte[] data) {
			return createMultipartUpload().uploadAsync(data);
		}

		/**
		 * Uploads data from a Spring Resource using multipart upload asynchronously.
		 * @param resource the Spring Resource to read data from
		 * @return a CompletableFuture that will complete with the upload result
		 */
		public CompletableFuture<CompleteMultipartUploadResult> uploadAsync(Resource resource) {
			return createMultipartUpload().uploadAsync(resource);
		}

		private MultipartUpload createMultipartUpload() {
			S3Request baseRequest = prepareRequest(HttpMethod.PUT, b -> b.bucket(bucketName).key(objectKey));
			return new MultipartUpload(restClient, baseRequest, configuration, progressCallback);
		}

	}

	/**
	 * Builder class for range request operations.
	 */
	public class RangeRequestBuilder {

		private final String bucketName;

		private final String objectKey;

		private final Long rangeStart;

		private final Long rangeEnd;

		private RangeRequestBuilder(String bucketName, String objectKey, Long rangeStart, Long rangeEnd) {
			this.bucketName = bucketName;
			this.objectKey = objectKey;
			this.rangeStart = rangeStart;
			this.rangeEnd = rangeEnd;
		}

		/**
		 * Gets the partial content as a Spring Resource for streaming.
		 * @return The partial object content as a Spring Resource
		 */
		public Resource getAsResource() {
			S3Request rangeRequest = createRangeRequest();
			return executeGetRequest(rangeRequest, Resource.class);
		}

		/**
		 * Gets the partial content as a byte array.
		 * @return The partial object content as a byte array
		 */
		public byte[] getAsBytes() {
			S3Request rangeRequest = createRangeRequest();
			return executeGetRequest(rangeRequest, byte[].class);
		}

		/**
		 * Gets the partial content as a string.
		 * @return The partial object content as a string
		 */
		public String getAsString() {
			S3Request rangeRequest = createRangeRequest();
			return executeGetRequest(rangeRequest, String.class);
		}

		/**
		 * Creates a range request for partial content retrieval.
		 * @return A configured S3Request with range headers
		 */
		private S3Request createRangeRequest() {
			S3Request request = prepareRequest(HttpMethod.GET, b -> b.bucket(bucketName).key(objectKey));
			return rangeEnd != null ? request.withRange(rangeStart, rangeEnd) : request.withRangeFrom(rangeStart);
		}

	}

	/**
	 * Builder class for version-specific object operations.
	 *
	 * @since 0.3.0
	 */
	public class VersionedObjectOperationBuilder {

		private final String bucketName;

		private final String objectKey;

		private final String versionId;

		private VersionedObjectOperationBuilder(String bucketName, String objectKey, String versionId) {
			this.bucketName = bucketName;
			this.objectKey = objectKey;
			this.versionId = versionId;
		}

		/**
		 * Gets (downloads) a specific version of an object as a string.
		 * @return The versioned object content as a string
		 */
		public String getAsString() {
			S3Request request = createVersionedRequest(HttpMethod.GET);
			return executeGetRequest(request, String.class);
		}

		/**
		 * Gets (downloads) a specific version of an object as a byte array.
		 * @return The versioned object content as a byte array
		 */
		public byte[] getAsBytes() {
			S3Request request = createVersionedRequest(HttpMethod.GET);
			return executeGetRequest(request, byte[].class);
		}

		/**
		 * Gets (downloads) a specific version of an object as a Spring Resource.
		 * @return The versioned object content as a Spring Resource
		 */
		public Resource getAsResource() {
			S3Request request = createVersionedRequest(HttpMethod.GET);
			return executeGetRequest(request, Resource.class);
		}

		/**
		 * Deletes a specific version of the object.
		 */
		public void delete() {
			S3Request request = createVersionedRequest(HttpMethod.DELETE).deleteVersion(versionId);
			executeDeleteRequest(request);
		}

		/**
		 * Copies a specific version of this object to a new location.
		 * @param destinationBucket the destination bucket name
		 * @param destinationKey the destination object key
		 */
		public void copyTo(String destinationBucket, String destinationKey) {
			String copySource = "/" + bucketName + "/" + objectKey + "?versionId=" + versionId;
			Map<String, String> additionalHeaders = Map.of("x-amz-copy-source", copySource);

			S3Request request = prepareRequestBuilder(HttpMethod.PUT,
					b -> b.bucket(destinationBucket).key(destinationKey))
				.additionalHeaders(additionalHeaders)
				.build();
			executePutRequest(request);
		}

		/**
		 * Generates a presigned URL for this specific version with the specified HTTP
		 * method.
		 * @param method the HTTP method (GET or DELETE)
		 * @param expiration the duration until the URL expires
		 * @return a PresignedUrl containing the URL and expiration information
		 */
		public PresignedUrl presignedUrl(HttpMethod method, java.time.Duration expiration) {
			if (!(method == HttpMethod.GET || method == HttpMethod.DELETE)) {
				throw new IllegalArgumentException("Method not supported for versioned objects: " + method);
			}

			S3Request request = createVersionedRequest(method);

			return request.presignedUrl(expiration);
		}

		/**
		 * Creates a versioned request for this object.
		 * @param method the HTTP method
		 * @return A configured S3Request with version ID
		 */
		private S3Request createVersionedRequest(HttpMethod method) {
			return prepareRequest(method, b -> b.bucket(bucketName).key(objectKey)).withVersionId(versionId);
		}

	}

}