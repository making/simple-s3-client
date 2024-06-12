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

import java.util.List;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
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
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static am.ik.s3.S3RequestBuilder.s3Request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

@Testcontainers(disabledWithoutDocker = true)
class RestTemplateTest {

	DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:0.11.3");

	@Container
	public LocalStackContainer localstack = new LocalStackContainer(localstackImage)
		.withServices(LocalStackContainer.Service.S3);

	RestTemplate restTemplate;

	@BeforeEach
	void setup() {
		this.restTemplate = new RestTemplate();
		this.restTemplate.getInterceptors()
			.add(new LogbookClientHttpRequestInterceptor(Logbook.builder()
				.sink(new AccessLoggerSink())
				.headerFilter(OpinionatedFilters.headerFilter())
				.build()));
	}

	private S3RequestBuilders.Method partialS3Request() {
		return s3Request().endpoint(localstack.getEndpoint())
			.region(localstack.getRegion())
			.accessKeyId(localstack.getAccessKey())
			.secretAccessKey(localstack.getSecretKey());
	}

	@Test
	void scenario() throws Exception {
		String bucketName = "test";
		{
			S3Request request = partialS3Request().method(PUT).path(b -> b.bucket(bucketName)).build();
			this.restTemplate.exchange(request.toEntityBuilder().build(), Void.class);
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b).build();
			ListBucketsResult listBucketsResult = this.restTemplate
				.exchange(request.toEntityBuilder().build(), ListBucketsResult.class)
				.getBody();
			List<Bucket> buckets = listBucketsResult.buckets();
			assertThat(buckets).hasSize(1);
			assertThat(buckets.get(0).name()).isEqualTo("test");
		}
		{
			String body = "Hello World!";
			S3Request request = partialS3Request().method(PUT)
				.path(b -> b.bucket(bucketName).key("hello.txt"))
				.content(S3Content.of(body, MediaType.TEXT_PLAIN))
				.build();
			this.restTemplate.exchange(request.toEntityBuilder().body(body), Void.class);
		}
		{
			byte[] body = new ClassPathResource("test.png").getContentAsByteArray();
			S3Request request = partialS3Request().method(PUT)
				.path(b -> b.bucket(bucketName).key("test.png"))
				.content(S3Content.of(body, MediaType.IMAGE_PNG))
				.build();
			this.restTemplate.exchange(request.toEntityBuilder().body(body), Void.class);
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b.bucket(bucketName)).build();
			ListBucketResult listBucketResult = this.restTemplate
				.exchange(request.toEntityBuilder().build(), ListBucketResult.class)
				.getBody();
			assertThat(listBucketResult.name()).isEqualTo("test");
			List<Content> contents = listBucketResult.contents();
			assertThat(contents).hasSize(2);
			assertThat(contents.get(0).key()).isEqualTo("hello.txt");
			assertThat(contents.get(1).key()).isEqualTo("test.png");
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b.bucket(bucketName).key("hello.txt")).build();
			String body = this.restTemplate.exchange(request.toEntityBuilder().build(), String.class).getBody();
			assertThat(body).isEqualTo("Hello World!");
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b.bucket(bucketName).key("test.png")).build();
			byte[] body = this.restTemplate.exchange(request.toEntityBuilder().build(), byte[].class).getBody();
			assertThat(body).isEqualTo(new ClassPathResource("test.png").getContentAsByteArray());
		}
		{
			S3Request request = partialS3Request().method(DELETE)
				.path(b -> b.bucket(bucketName).key("hello.txt"))
				.build();
			this.restTemplate.exchange(request.toEntityBuilder().build(), Void.class);
		}

		{
			S3Request request = partialS3Request().method(GET).path(b -> b.bucket(bucketName)).build();
			ListBucketResult listBucketResult = this.restTemplate
				.exchange(request.toEntityBuilder().build(), ListBucketResult.class)
				.getBody();
			assertThat(listBucketResult.name()).isEqualTo("test");
			List<Content> contents = listBucketResult.contents();
			assertThat(contents).hasSize(1);
			assertThat(contents.get(0).key()).isEqualTo("test.png");
		}
		{
			S3Request request = partialS3Request().method(DELETE)
				.path(b -> b.bucket(bucketName).key("test.png"))
				.build();
			this.restTemplate.exchange(request.toEntityBuilder().build(), Void.class);
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b.bucket(bucketName)).build();
			ListBucketResult listBucketResult = this.restTemplate
				.exchange(request.toEntityBuilder().build(), ListBucketResult.class)
				.getBody();
			assertThat(listBucketResult.name()).isEqualTo("test");
			List<Content> contents = listBucketResult.contents();
			assertThat(contents).isNullOrEmpty();
		}
		{
			S3Request request = partialS3Request().method(DELETE).path(b -> b.bucket(bucketName)).build();
			this.restTemplate.exchange(request.toEntityBuilder().build(), Void.class);
		}
		{
			S3Request request = partialS3Request().method(GET).path(b -> b).build();
			ListBucketsResult listBucketsResult = this.restTemplate
				.exchange(request.toEntityBuilder().build(), ListBucketsResult.class)
				.getBody();
			assertThat(listBucketsResult.buckets()).isNullOrEmpty();
		}
	}

}