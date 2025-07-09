package am.ik.s3;

import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Handles multipart upload operations for large files. Provides both synchronous and
 * asynchronous upload capabilities with progress tracking.
 *
 * @since 0.3.0
 */
public final class MultipartUpload {

	private final RestClient restClient;

	private final S3Request baseRequest;

	private final MultipartUploadConfiguration configuration;

	private final ProgressCallback progressCallback;

	/**
	 * Creates a new MultipartUpload instance.
	 * @param restClient the RestClient to use for HTTP operations
	 * @param baseRequest the base S3Request for the object
	 * @param configuration the multipart upload configuration
	 * @param progressCallback the progress callback for tracking upload progress
	 */
	public MultipartUpload(RestClient restClient, S3Request baseRequest, MultipartUploadConfiguration configuration,
			ProgressCallback progressCallback) {
		this.restClient = restClient;
		this.baseRequest = baseRequest;
		this.configuration = configuration;
		this.progressCallback = progressCallback != null ? progressCallback : ProgressCallback.noOp();
	}

	/**
	 * Uploads data using multipart upload.
	 * @param data the data to upload
	 * @return the result of the completed multipart upload
	 * @throws RuntimeException if the upload fails
	 */
	public CompleteMultipartUploadResult upload(byte[] data) {
		return upload(new ByteArrayResource(data));
	}

	/**
	 * Uploads data from a Spring Resource using multipart upload.
	 * @param resource the Spring Resource to read data from
	 * @return the result of the completed multipart upload
	 * @throws RuntimeException if the upload fails
	 */
	public CompleteMultipartUploadResult upload(Resource resource) {
		try {
			long contentLength = resource.contentLength();
			try (InputStream inputStream = resource.getInputStream()) {
				// Step 1: Initiate multipart upload
				InitiateMultipartUploadResult initResult = initiateUpload();
				String uploadId = initResult.uploadId();

				try {
					// Step 2: Calculate parts and upload them
					List<CompletedPart> completedParts = uploadParts(inputStream, contentLength, uploadId);

					// Step 3: Complete multipart upload
					CompleteMultipartUpload completeRequest = new CompleteMultipartUpload(completedParts);
					CompleteMultipartUploadResult result = completeUpload(uploadId, completeRequest);

					progressCallback.onUploadCompleted(contentLength);
					return result;

				}
				catch (Exception e) {
					// Abort the multipart upload on failure
					try {
						abortUpload(uploadId);
					}
					catch (Exception abortException) {
						e.addSuppressed(abortException);
					}
					progressCallback.onError(e);
					throw new RuntimeException("Multipart upload failed", e);
				}
			}
		}
		catch (IOException e) {
			progressCallback.onError(e);
			throw new RuntimeException("Failed to read resource", e);
		}
	}

	/**
	 * Uploads data using multipart upload asynchronously.
	 * @param data the data to upload
	 * @return a CompletableFuture that will complete with the upload result
	 */
	public CompletableFuture<CompleteMultipartUploadResult> uploadAsync(byte[] data) {
		if (configuration.executor() != null) {
			return CompletableFuture.supplyAsync(() -> upload(data), configuration.executor());
		}
		else {
			return CompletableFuture.supplyAsync(() -> upload(data));
		}
	}

	/**
	 * Uploads data from a Spring Resource using multipart upload asynchronously.
	 * @param resource the Spring Resource to read data from
	 * @return a CompletableFuture that will complete with the upload result
	 */
	public CompletableFuture<CompleteMultipartUploadResult> uploadAsync(Resource resource) {
		if (configuration.executor() != null) {
			return CompletableFuture.supplyAsync(() -> upload(resource), configuration.executor());
		}
		else {
			return CompletableFuture.supplyAsync(() -> upload(resource));
		}
	}

	private InitiateMultipartUploadResult initiateUpload() {
		S3Request request = baseRequest.initiateMultipartUpload();
		return restClient.post()
			.uri(request.uri())
			.headers(request.headers())
			.retrieve()
			.body(InitiateMultipartUploadResult.class);
	}

	private List<CompletedPart> uploadParts(InputStream inputStream, long contentLength, String uploadId)
			throws IOException {
		long partSizeBytes = configuration.partSize().toBytes();
		int totalParts = (int) Math.ceil((double) contentLength / partSizeBytes);

		if (totalParts > MultipartUploadConfiguration.MAX_PARTS) {
			throw new IllegalArgumentException("File too large for current part size. Total parts: " + totalParts
					+ ", max allowed: " + MultipartUploadConfiguration.MAX_PARTS);
		}

		List<CompletedPart> completedParts = new ArrayList<>();

		if (configuration.maxConcurrentUploads() == 1) {
			// Sequential upload
			for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
				byte[] partData = readPartData(inputStream, partSizeBytes, partNumber == totalParts, contentLength);
				UploadPartResult result = uploadSinglePart(uploadId, partNumber, partData);
				completedParts.add(new CompletedPart(result.etag(), result.partNumber()));

				if (configuration.enableProgressTracking()) {
					progressCallback.onPartCompleted(partNumber, partData.length);
				}
			}
		}
		else {
			// Parallel upload
			completedParts = uploadPartsInParallel(inputStream, contentLength, uploadId, partSizeBytes, totalParts);
		}

		return completedParts;
	}

	private List<CompletedPart> uploadPartsInParallel(InputStream inputStream, long contentLength, String uploadId,
			long partSizeBytes, int totalParts) throws IOException {

		ExecutorService executor = Executors.newFixedThreadPool(configuration.maxConcurrentUploads());
		List<Future<CompletedPart>> futures = new ArrayList<>();
		List<CompletedPart> completedParts = new ArrayList<>();

		try {
			// Read all data first for parallel processing
			byte[] allData = inputStream.readAllBytes();

			for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
				final int currentPartNumber = partNumber;
				long startOffset = (partNumber - 1) * partSizeBytes;
				long endOffset = Math.min(startOffset + partSizeBytes, allData.length);
				byte[] partData = Arrays.copyOfRange(allData, (int) startOffset, (int) endOffset);

				Future<CompletedPart> future = executor.submit(() -> {
					try {
						UploadPartResult result = uploadSinglePart(uploadId, currentPartNumber, partData);
						if (configuration.enableProgressTracking()) {
							progressCallback.onPartCompleted(currentPartNumber, partData.length);
						}
						return new CompletedPart(result.etag(), result.partNumber());
					}
					catch (Exception e) {
						throw new RuntimeException("Failed to upload part " + currentPartNumber, e);
					}
				});
				futures.add(future);
			}

			// Collect results in order
			for (Future<CompletedPart> future : futures) {
				completedParts.add(future.get());
			}

		}
		catch (Exception e) {
			throw new RuntimeException("Parallel upload failed", e);
		}
		finally {
			executor.shutdown();
		}

		return completedParts;
	}

	private byte[] readPartData(InputStream inputStream, long partSizeBytes, boolean isLastPart, long totalLength)
			throws IOException {
		if (isLastPart) {
			// For the last part, read remaining bytes
			return inputStream.readAllBytes();
		}
		else {
			// Read exactly partSizeBytes
			byte[] buffer = new byte[(int) partSizeBytes];
			int bytesRead = inputStream.readNBytes(buffer, 0, buffer.length);
			if (bytesRead < buffer.length) {
				// Return only the bytes that were actually read
				return Arrays.copyOf(buffer, bytesRead);
			}
			return buffer;
		}
	}

	private UploadPartResult uploadSinglePart(String uploadId, int partNumber, byte[] partData) {
		S3Request request = baseRequest.uploadPart(uploadId, partNumber, partData);
		var response = restClient.put()
			.uri(request.uri())
			.headers(request.headers())
			.body(partData)
			.retrieve()
			.toEntity(Void.class);

		String etag = response.getHeaders().getFirst("ETag");
		if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
			etag = etag.substring(1, etag.length() - 1);
		}

		return new UploadPartResult(etag, partNumber);
	}

	private CompleteMultipartUploadResult completeUpload(String uploadId, CompleteMultipartUpload completeRequest) {
		S3Request request = baseRequest.completeMultipartUpload(uploadId, completeRequest);
		return restClient.post()
			.uri(request.uri())
			.headers(request.headers())
			.body(completeRequest)
			.retrieve()
			.body(CompleteMultipartUploadResult.class);
	}

	private void abortUpload(String uploadId) {
		S3Request request = baseRequest.abortMultipartUpload(uploadId);
		restClient.delete().uri(request.uri()).headers(request.headers()).retrieve().toBodilessEntity();
	}

	/**
	 * Lists the parts of an ongoing multipart upload.
	 * @param uploadId the upload ID
	 * @return the list of parts
	 */
	public ListPartsResult listParts(String uploadId) {
		S3Request request = baseRequest.listParts(uploadId);
		return restClient.get().uri(request.uri()).headers(request.headers()).retrieve().body(ListPartsResult.class);
	}

}