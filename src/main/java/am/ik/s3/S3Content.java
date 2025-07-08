/*
 * Copyright (C) 2023 Toshiaki Maki <makingx@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package am.ik.s3;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * Represents content to be sent in an S3 request. This is a sealed interface with two
 * implementations: - ByteArrayS3Content for in-memory content - StreamS3Content for
 * streaming content
 *
 * @since 0.3.0
 */
public sealed interface S3Content permits S3Content.ByteArrayS3Content, S3Content.StreamS3Content {

	/**
	 * Creates S3Content from a string.
	 * @param body the content body as a string
	 * @param mediaType the media type of the content
	 * @return a new ByteArrayS3Content instance
	 */
	static S3Content of(String body, MediaType mediaType) {
		return new ByteArrayS3Content(body.getBytes(StandardCharsets.UTF_8), mediaType);
	}

	/**
	 * Creates S3Content from a byte array.
	 * @param body the content body as a byte array
	 * @param mediaType the media type of the content
	 * @return a new ByteArrayS3Content instance
	 */
	static S3Content of(byte[] body, MediaType mediaType) {
		return new ByteArrayS3Content(body, mediaType);
	}

	/**
	 * Creates streaming S3Content with content length and media type.
	 * @param contentLength the length of the content in bytes
	 * @param mediaType the media type of the content
	 * @return a new StreamS3Content instance
	 * @since 0.3.0
	 */
	static S3Content ofStream(long contentLength, MediaType mediaType) {
		return new StreamS3Content(contentLength, mediaType);
	}

	/**
	 * Creates streaming S3Content with content length and default media type.
	 * @param contentLength the length of the content in bytes
	 * @return a new StreamS3Content instance
	 * @since 0.3.0
	 */
	static S3Content ofStream(long contentLength) {
		return new StreamS3Content(contentLength, MediaType.APPLICATION_OCTET_STREAM);
	}

	/**
	 * Represents in-memory content to be sent in an S3 request.
	 *
	 * @param body the content body as a byte array
	 * @param mediaType the media type of the content
	 */
	record ByteArrayS3Content(byte[] body, MediaType mediaType) implements S3Content {
	}

	/**
	 * Represents streaming content to be sent in an S3 request. This allows for
	 * memory-efficient uploads of large files.
	 *
	 * @param contentLength the length of the content in bytes
	 * @param mediaType the media type of the content
	 * @since 0.3.0
	 */
	record StreamS3Content(long contentLength, MediaType mediaType) implements S3Content {
	}

}
