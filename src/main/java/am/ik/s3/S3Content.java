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

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Represents content to be sent in an S3 request. This is a sealed interface with two
 * implementations: - ByteArrayS3Content for in-memory content - ResourceS3Content for
 * Spring Resource based content
 *
 * @since 0.3.0
 */
public sealed interface S3Content permits S3Content.ByteArrayS3Content, S3Content.ResourceS3Content {

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
	 * Creates resource-based S3Content with a Spring Resource and media type.
	 * @param resource the Spring Resource containing the content
	 * @param mediaType the media type of the content
	 * @return a new ResourceS3Content instance
	 * @since 0.3.0
	 */
	static S3Content ofResource(Resource resource, MediaType mediaType) {
		return new ResourceS3Content(resource, mediaType);
	}

	/**
	 * Creates resource-based S3Content with a Spring Resource and default media type.
	 * @param resource the Spring Resource containing the content
	 * @return a new ResourceS3Content instance
	 * @since 0.3.0
	 */
	static S3Content ofResource(Resource resource) {
		return new ResourceS3Content(resource, MediaType.APPLICATION_OCTET_STREAM);
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
	 * Represents resource-based content to be sent in an S3 request. This allows for
	 * direct use of Spring's Resource abstraction for file uploads.
	 *
	 * @param resource the Spring Resource containing the content
	 * @param mediaType the media type of the content
	 * @since 0.3.0
	 */
	record ResourceS3Content(Resource resource, MediaType mediaType) implements S3Content {
	}

}
