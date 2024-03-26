package am.ik.s3;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

public record S3Content(byte[] body, MediaType mediaType) {
	public static S3Content of(String body, MediaType mediaType) {
		return new S3Content(body.getBytes(StandardCharsets.UTF_8), mediaType);
	}

	public static S3Content of(byte[] body, MediaType mediaType) {
		return new S3Content(body, mediaType);
	}
}
