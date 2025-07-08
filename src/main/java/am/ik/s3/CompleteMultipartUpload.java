package am.ik.s3;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Request body for CompleteMultipartUpload operation. Contains the list of completed
 * parts with their ETags.
 *
 * @since 0.3.0
 */
@JacksonXmlRootElement(localName = "CompleteMultipartUpload")
public record CompleteMultipartUpload(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(
		localName = "Part") List<CompletedPart> parts) {
}