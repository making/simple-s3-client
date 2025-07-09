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

/**
 * Constants for Amazon S3 HTTP headers.
 */
public class AmzHttpHeaders {

	static final String X_AMZ_CONTENT_SHA256 = "X-Amz-Content-Sha256";

	static final String X_AMZ_DATE = "X-Amz-Date";

	static final String X_AMZ_ALGORITHM = "X-Amz-Algorithm";

	static final String X_AMZ_CREDENTIAL = "X-Amz-Credential";

	static final String X_AMZ_EXPIRES = "X-Amz-Expires";

	static final String X_AMZ_SIGNED_HEADERS = "X-Amz-SignedHeaders";

	static final String X_AMZ_SIGNATURE = "X-Amz-Signature";

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private AmzHttpHeaders() {
	}

}
