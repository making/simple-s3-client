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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class S3ClientTest {

	DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:0.11.3");

	@Container
	public LocalStackContainer localstack = new LocalStackContainer(localstackImage)
		.withServices(LocalStackContainer.Service.S3);

	S3Client s3Client;

	@BeforeEach
	void setup() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors().add(new LogbookClientHttpRequestInterceptor(Logbook.builder().build()));
		this.s3Client = new S3Client(restTemplate, localstack.getEndpoint(), localstack.getRegion(),
				localstack.getAccessKey(), localstack.getSecretKey());
	}

	@Test
	void scenario() throws Exception {
		String bucketName = "test";
		this.s3Client.putBucket(bucketName);
		{
			ListBucketsResult listBucketsResult = this.s3Client.listBuckets();
			List<Bucket> buckets = listBucketsResult.buckets();
			assertThat(buckets).hasSize(1);
			assertThat(buckets.get(0).name()).isEqualTo("test");
		}
		this.s3Client.putObject(bucketName, "hello.txt", "Hello World!".getBytes(StandardCharsets.UTF_8),
				MediaType.TEXT_PLAIN);
		this.s3Client.putObject(bucketName, "test.png", new ClassPathResource("test.png"), MediaType.IMAGE_PNG);
		{
			ListBucketResult listBucketResult = this.s3Client.listBucket(bucketName);
			assertThat(listBucketResult.name()).isEqualTo("test");
			List<Content> contents = listBucketResult.contents();
			assertThat(contents).hasSize(2);
			assertThat(contents.get(0).key()).isEqualTo("hello.txt");
			assertThat(contents.get(1).key()).isEqualTo("test.png");
		}
		assertThat(new String(this.s3Client.getObject(bucketName, "hello.txt"))).isEqualTo("Hello World!");
		assertThat(this.s3Client.getObject(bucketName, "test.png"))
			.isEqualTo(new ClassPathResource("test.png").getContentAsByteArray());
		this.s3Client.deleteObject(bucketName, "hello.txt");
		{
			ListBucketResult listBucketResult = this.s3Client.listBucket(bucketName);
			assertThat(listBucketResult.name()).isEqualTo("test");
			List<Content> contents = listBucketResult.contents();
			assertThat(contents).hasSize(1);
			assertThat(contents.get(0).key()).isEqualTo("test.png");
		}
		this.s3Client.deleteObject(bucketName, "test.png");
		{
			ListBucketResult listBucketResult = this.s3Client.listBucket(bucketName);
			assertThat(listBucketResult.name()).isEqualTo("test");
			assertThat(listBucketResult.contents()).isNullOrEmpty();
		}
		this.s3Client.deleteBucket(bucketName);
		{
			ListBucketsResult listBucketsResult = this.s3Client.listBuckets();
			assertThat(listBucketsResult.buckets()).isNullOrEmpty();
		}
	}

}