package am.ik.s3;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response from CompleteMultipartUpload operation. Contains the final object information
 * after successful multipart upload completion.
 *
 * @since 0.3.0
 */
@JacksonXmlRootElement(localName = "CompleteMultipartUploadResult")
public record CompleteMultipartUploadResult(@JacksonXmlProperty(localName = "Location") String location,
		@JacksonXmlProperty(localName = "Bucket") String bucket, @JacksonXmlProperty(localName = "Key") String key,
		@JacksonXmlProperty(localName = "ETag") String etag) {
}