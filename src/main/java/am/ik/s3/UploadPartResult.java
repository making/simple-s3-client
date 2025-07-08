package am.ik.s3;

/**
 * Response from UploadPart operation. Contains the ETag required for completing multipart
 * upload.
 *
 * @since 0.3.0
 */
public record UploadPartResult(String etag, int partNumber) {
}