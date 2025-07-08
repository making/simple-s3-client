package am.ik.s3;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response from ListParts operation. Contains information about parts that have been
 * uploaded for a multipart upload.
 *
 * @since 0.3.0
 */
@JacksonXmlRootElement(localName = "ListPartsResult")
public record ListPartsResult(@JacksonXmlProperty(localName = "Bucket") String bucket,
		@JacksonXmlProperty(localName = "Key") String key, @JacksonXmlProperty(localName = "UploadId") String uploadId,
		@JacksonXmlProperty(localName = "Initiator") Owner initiator,
		@JacksonXmlProperty(localName = "Owner") Owner owner,
		@JacksonXmlProperty(localName = "StorageClass") String storageClass,
		@JacksonXmlProperty(localName = "PartNumberMarker") Integer partNumberMarker,
		@JacksonXmlProperty(localName = "NextPartNumberMarker") Integer nextPartNumberMarker,
		@JacksonXmlProperty(localName = "MaxParts") Integer maxParts,
		@JacksonXmlProperty(localName = "IsTruncated") Boolean isTruncated,
		@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "Part") List<Part> parts) {

	/**
	 * Represents a part in a multipart upload.
	 */
	public record Part(@JacksonXmlProperty(localName = "PartNumber") int partNumber,
			@JacksonXmlProperty(localName = "LastModified") String lastModified,
			@JacksonXmlProperty(localName = "ETag") String etag, @JacksonXmlProperty(localName = "Size") long size) {
	}
}