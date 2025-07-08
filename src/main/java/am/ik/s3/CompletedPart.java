package am.ik.s3;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Represents a completed part in a multipart upload. Used in CompleteMultipartUpload
 * request.
 *
 * @since 0.3.0
 */
public record CompletedPart(@JacksonXmlProperty(localName = "ETag") String etag,
		@JacksonXmlProperty(localName = "PartNumber") int partNumber) {
}