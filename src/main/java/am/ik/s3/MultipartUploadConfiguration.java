package am.ik.s3;

import org.springframework.util.unit.DataSize;

import java.util.concurrent.Executor;

/**
 * Configuration for multipart upload operations. Provides settings for part size,
 * concurrency, and other upload parameters.
 *
 * @since 0.3.0
 */
public record MultipartUploadConfiguration(DataSize partSize, int maxConcurrentUploads, boolean enableProgressTracking,
		Executor executor) {

	/**
	 * Default part size for multipart uploads (5MB).
	 */
	public static final DataSize DEFAULT_PART_SIZE = DataSize.ofMegabytes(5);

	/**
	 * Maximum part size allowed by S3 (5GB).
	 */
	public static final DataSize MAX_PART_SIZE = DataSize.ofGigabytes(5);

	/**
	 * Minimum part size allowed by S3 (5MB).
	 */
	public static final DataSize MIN_PART_SIZE = DataSize.ofMegabytes(5);

	/**
	 * Default maximum concurrent uploads.
	 */
	public static final int DEFAULT_MAX_CONCURRENT_UPLOADS = 3;

	/**
	 * Maximum number of parts allowed by S3.
	 */
	public static final int MAX_PARTS = 10000;

	/**
	 * Creates a default configuration with 5MB part size and 3 concurrent uploads.
	 * @return a default MultipartUploadConfiguration
	 */
	public static MultipartUploadConfiguration defaultConfiguration() {
		return new MultipartUploadConfiguration(DEFAULT_PART_SIZE, DEFAULT_MAX_CONCURRENT_UPLOADS, true, null);
	}

	/**
	 * Creates a new configuration builder.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Validates the configuration parameters.
	 * @throws IllegalArgumentException if any parameter is invalid
	 */
	public void validate() {
		if (partSize.compareTo(MIN_PART_SIZE) < 0) {
			throw new IllegalArgumentException("Part size must be at least " + MIN_PART_SIZE);
		}
		if (partSize.compareTo(MAX_PART_SIZE) > 0) {
			throw new IllegalArgumentException("Part size must not exceed " + MAX_PART_SIZE);
		}
		if (maxConcurrentUploads <= 0) {
			throw new IllegalArgumentException("Max concurrent uploads must be positive");
		}
	}

	/**
	 * Builder for MultipartUploadConfiguration.
	 */
	public static final class Builder {

		private DataSize partSize = DEFAULT_PART_SIZE;

		private int maxConcurrentUploads = DEFAULT_MAX_CONCURRENT_UPLOADS;

		private boolean enableProgressTracking = true;

		private Executor executor = null;

		private Builder() {
		}

		/**
		 * Sets the part size for multipart uploads.
		 * @param partSize the part size (must be between 5MB and 5GB)
		 * @return this builder
		 */
		public Builder partSize(DataSize partSize) {
			this.partSize = partSize;
			return this;
		}

		/**
		 * Sets the maximum number of concurrent part uploads.
		 * @param maxConcurrentUploads the maximum concurrent uploads (must be positive)
		 * @return this builder
		 */
		public Builder maxConcurrentUploads(int maxConcurrentUploads) {
			this.maxConcurrentUploads = maxConcurrentUploads;
			return this;
		}

		/**
		 * Enables or disables progress tracking.
		 * @param enableProgressTracking true to enable progress tracking
		 * @return this builder
		 */
		public Builder enableProgressTracking(boolean enableProgressTracking) {
			this.enableProgressTracking = enableProgressTracking;
			return this;
		}

		/**
		 * Sets the executor for asynchronous operations.
		 * @param executor the executor to use for async operations (null to use default)
		 * @return this builder
		 */
		public Builder executor(Executor executor) {
			this.executor = executor;
			return this;
		}

		/**
		 * Builds the configuration.
		 * @return a new MultipartUploadConfiguration instance
		 * @throws IllegalArgumentException if any parameter is invalid
		 */
		public MultipartUploadConfiguration build() {
			MultipartUploadConfiguration config = new MultipartUploadConfiguration(partSize, maxConcurrentUploads,
					enableProgressTracking, executor);
			config.validate();
			return config;
		}

	}
}