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

import org.springframework.http.MediaType;

/**
 * Represents content to be sent in an S3 request.
 *
 * @param body the content body as a byte array
 * @param mediaType the media type of the content
 */
public record S3Content(byte[] body, MediaType mediaType) {
	/**
	 * Creates S3Content from a string.
	 * @param body the content body as a string
	 * @param mediaType the media type of the content
	 * @return a new S3Content instance
	 */
	public static S3Content of(String body, MediaType mediaType) {
		return new S3Content(body.getBytes(StandardCharsets.UTF_8), mediaType);
	}

	/**
	 * Creates S3Content from a byte array.
	 * @param body the content body as a byte array
	 * @param mediaType the media type of the content
	 * @return a new S3Content instance
	 */
	public static S3Content of(byte[] body, MediaType mediaType) {
		return new S3Content(body, mediaType);
	}
}
