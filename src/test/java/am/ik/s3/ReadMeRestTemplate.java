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
import java.util.UUID;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import static am.ik.s3.S3RequestBuilder.s3Request;

public class ReadMeRestTemplate {

	public static void main(String[] args) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors()
			.add(new LogbookClientHttpRequestInterceptor(Logbook.builder()
				.sink(new AccessLoggerSink())
				.headerFilter(OpinionatedFilters.headerFilter())
				.build()));

		URI endpoint = URI.create("https://play.min.io");
		String region = "us-east-1";
		String accessKeyId = "Q3AM3UQ867SPQQA43P2F";
		String secretAccessKey = "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG";
		String bucket = UUID.randomUUID().toString();

		S3Request putBucketRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket))
			.build();
		restTemplate.exchange(putBucketRequest.toEntityBuilder().build(), Void.class);

		S3Request listBucketsRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b)
			.build();
		ListBucketsResult bucketsResult = restTemplate
			.exchange(listBucketsRequest.toEntityBuilder().build(), ListBucketsResult.class)
			.getBody();
		System.out.println(bucketsResult);

		String body = "Hello World!";
		S3Request putObjectRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.content(S3Content.of(body, MediaType.TEXT_PLAIN))
			.build();
		restTemplate.exchange(putObjectRequest.toEntityBuilder().body(body), Void.class);

		S3Request listBucketRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket))
			.build();
		ListBucketResult bucketResult = restTemplate
			.exchange(listBucketRequest.toEntityBuilder().build(), ListBucketResult.class)
			.getBody();
		System.out.println(bucketResult);

		S3Request getObjectRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.build();
		String response = restTemplate.exchange(getObjectRequest.toEntityBuilder().build(), String.class).getBody();
		System.out.println("Response: " + response); // Response: Hello World!

		S3Request deleteObjectRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.build();
		restTemplate.exchange(deleteObjectRequest.toEntityBuilder().build(), Void.class);

		S3Request deleteBucketRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket))
			.build();
		restTemplate.exchange(deleteBucketRequest.toEntityBuilder().build(), Void.class);
	}

}
