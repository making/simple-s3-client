package am.ik.s3;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response from InitiateMultipartUpload operation. Contains the upload ID required for
 * subsequent multipart upload operations.
 *
 * @since 0.3.0
 */
@JacksonXmlRootElement(localName = "InitiateMultipartUploadResult")
public record InitiateMultipartUploadResult(@JacksonXmlProperty(localName = "Bucket") String bucket,
		@JacksonXmlProperty(localName = "Key") String key,
		@JacksonXmlProperty(localName = "UploadId") String uploadId) {
}