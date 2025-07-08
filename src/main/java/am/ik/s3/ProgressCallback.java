package am.ik.s3;

/**
 * Callback interface for tracking multipart upload progress. Provides notifications for
 * part completion, upload completion, and errors.
 *
 * @since 0.3.0
 */
public interface ProgressCallback {

	/**
	 * Called when a part upload is completed.
	 * @param partNumber the part number that was completed (1-based)
	 * @param bytesTransferred the number of bytes transferred for this part
	 */
	void onPartCompleted(int partNumber, long bytesTransferred);

	/**
	 * Called when the entire multipart upload is completed.
	 * @param totalBytes the total number of bytes transferred
	 */
	void onUploadCompleted(long totalBytes);

	/**
	 * Called when an error occurs during upload.
	 * @param error the exception that occurred
	 */
	void onError(Exception error);

	/**
	 * Creates a no-op progress callback that does nothing.
	 * @return a ProgressCallback that ignores all events
	 */
	static ProgressCallback noOp() {
		return new ProgressCallback() {
			@Override
			public void onPartCompleted(int partNumber, long bytesTransferred) {
				// No operation
			}

			@Override
			public void onUploadCompleted(long totalBytes) {
				// No operation
			}

			@Override
			public void onError(Exception error) {
				// No operation
			}
		};
	}

	/**
	 * Creates a simple logging progress callback.
	 * @return a ProgressCallback that logs progress to standard output
	 */
	static ProgressCallback logging() {
		return new ProgressCallback() {
			@Override
			public void onPartCompleted(int partNumber, long bytesTransferred) {
				System.out.println("Part " + partNumber + " completed: " + bytesTransferred + " bytes");
			}

			@Override
			public void onUploadCompleted(long totalBytes) {
				System.out.println("Upload completed: " + totalBytes + " bytes total");
			}

			@Override
			public void onError(Exception error) {
				System.err.println("Upload error: " + error.getMessage());
			}
		};
	}

}