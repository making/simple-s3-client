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

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class PresignedPostFormTest {

	private static final String ENDPOINT = "https://s3.amazonaws.com";

	private static final String REGION = "us-east-1";

	private static final String ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

	private static final String SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

	private static final String BUCKET = "test-bucket";

	private static final String OBJECT_KEY = "uploads/test-file.txt";

	@Test
	void testPresignedPostFormGeneration() {
		S3Client client = S3Client.builder()
			.endpoint(ENDPOINT)
			.region(REGION)
			.credentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY)
			.build();

		PresignedPostForm postForm = client.bucket(BUCKET)
			.object(OBJECT_KEY)
			.presignedPostForm(Duration.ofHours(1))
			.maxFileSize(10 * 1024 * 1024) // 10MB
			.generate();

		assertThat(postForm.url()).isEqualTo(URI.create("https://s3.amazonaws.com/test-bucket"));
		assertThat(postForm.formFields()).containsKey("key");
		assertThat(postForm.formFields()).containsKey("bucket");
		assertThat(postForm.formFields()).containsKey("X-Amz-Algorithm");
		assertThat(postForm.formFields()).containsKey("X-Amz-Credential");
		assertThat(postForm.formFields()).containsKey("X-Amz-Date");
		assertThat(postForm.formFields()).containsKey("policy");
		assertThat(postForm.formFields()).containsKey("X-Amz-Signature");
		assertThat(postForm.formFields()).containsValue(OBJECT_KEY);
		assertThat(postForm.formFields()).containsValue(BUCKET);
		assertThat(postForm.formFields()).containsValue("AWS4-HMAC-SHA256");
	}

	@Test
	void testPresignedPostFormWithCustomFields() {
		S3Client client = S3Client.builder()
			.endpoint(ENDPOINT)
			.region(REGION)
			.credentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY)
			.build();

		PresignedPostForm postForm = client.bucket(BUCKET)
			.object(OBJECT_KEY)
			.presignedPostForm(Duration.ofMinutes(30))
			.maxFileSize(5 * 1024 * 1024) // 5MB
			.addField("Content-Type", "text/plain")
			.addField("Cache-Control", "max-age=3600")
			.generate();

		assertThat(postForm.formFields()).containsEntry("Content-Type", "text/plain");
		assertThat(postForm.formFields()).containsEntry("Cache-Control", "max-age=3600");
	}

	@Test
	void testPresignedPostFormWithDataSizeMaxFileSize() {
		S3Client client = S3Client.builder()
			.endpoint(ENDPOINT)
			.region(REGION)
			.credentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY)
			.build();

		PresignedPostForm postForm = client.bucket(BUCKET)
			.object(OBJECT_KEY)
			.presignedPostForm(Duration.ofHours(1))
			.maxFileSize(DataSize.ofMegabytes(20)) // 20MB using DataSize
			.addField("Content-Type", "application/json")
			.generate();

		assertThat(postForm.formFields()).containsKey("key");
		assertThat(postForm.formFields()).containsKey("bucket");
		assertThat(postForm.formFields()).containsEntry("Content-Type", "application/json");
		assertThat(postForm.formFields()).containsValue(OBJECT_KEY);
		assertThat(postForm.formFields()).containsValue(BUCKET);
	}

}