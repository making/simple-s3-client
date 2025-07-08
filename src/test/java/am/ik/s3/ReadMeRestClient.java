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
import java.util.UUID;

import am.ik.spring.logbook.AccessLoggerSink;
import am.ik.spring.logbook.OpinionatedFilters;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static am.ik.s3.S3RequestBuilder.s3Request;

public class ReadMeRestClient {

	public static void main(String[] args) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors()
			.add(new LogbookClientHttpRequestInterceptor(Logbook.builder().headerFilter(headers -> headers).build()));
		RestClient restClient = RestClient.builder()
			.requestInterceptor(new LogbookClientHttpRequestInterceptor(Logbook.builder()
				.sink(new AccessLoggerSink())
				.headerFilter(OpinionatedFilters.headerFilter())
				.build()))
			.messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
			.build();

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
		restClient.put().uri(putBucketRequest.uri()).headers(putBucketRequest.headers()).retrieve().toBodilessEntity();

		S3Request listBucketsRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b)
			.build();
		ListBucketsResult bucketsResult = restClient.get()
			.uri(listBucketsRequest.uri())
			.headers(listBucketsRequest.headers())
			.retrieve()
			.body(ListBucketsResult.class);
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
		restClient.put()
			.uri(putObjectRequest.uri())
			.headers(putObjectRequest.headers())
			.body(body)
			.retrieve()
			.toBodilessEntity();

		S3Request listBucketRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket))
			.build();
		ListBucketResult bucketResult = restClient.get()
			.uri(listBucketRequest.uri())
			.headers(listBucketRequest.headers())
			.retrieve()
			.body(ListBucketResult.class);
		System.out.println(bucketResult);

		S3Request getObjectRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.build();
		String response = restClient.get()
			.uri(getObjectRequest.uri())
			.headers(getObjectRequest.headers())
			.retrieve()
			.body(String.class);
		System.out.println("Response: " + response); // Response: Hello World!

		// Generate presigned URL for GET operation
		S3Request getRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.build();

		PresignedUrl presignedUrl = getRequest.presignedUrl(Duration.ofHours(1));
		System.out.println("Presigned URL: " + presignedUrl.url());

		// Use the presigned URL
		String content = restClient.get()
			.uri(presignedUrl.url())
			.headers(presignedUrl.headers())
			.retrieve()
			.body(String.class);

		// Generate presigned POST form for browser uploads
		S3Request postRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.POST) // Note: Use POST for S3Request
			.path(b -> b.bucket(bucket).key("uploads/file.txt"))
			.build();

		PresignedPostForm postForm = postRequest.presignedPostForm(Duration.ofHours(1))
			.maxFileSize(DataSize.ofMegabytes(10))
			.addField("Content-Type", "text/plain")
			.generate();

		System.out.println("POST URL: " + postForm.url());
		System.out.println("Form fields: " + postForm.formFields());

		// Use the presigned POST form to upload a file
		MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
		// Add all form fields from the presigned POST form
		postForm.formFields().forEach(formData::add);
		// Add the file content
		String fileContent = "This is a test file uploaded via presigned POST form";
		ByteArrayResource fileResource = new ByteArrayResource(fileContent.getBytes()) {
			@Override
			public String getFilename() {
				return "file.txt";
			}
		};
		formData.add("file", fileResource);

		// POST the form data
		restClient.post()
			.uri(postForm.url())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(formData)
			.retrieve()
			.toBodilessEntity();
		System.out.println("File uploaded successfully via presigned POST form");

		// Verify the uploaded file
		S3Request verifyRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key("uploads/file.txt"))
			.build();
		String uploadedContent = restClient.get()
			.uri(verifyRequest.uri())
			.headers(verifyRequest.headers())
			.retrieve()
			.body(String.class);
		System.out.println("Uploaded content: " + uploadedContent);

		// Clean up
		S3Request deleteObjectRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket).key("hello.txt"))
			.build();
		restClient.delete()
			.uri(deleteObjectRequest.uri())
			.headers(deleteObjectRequest.headers())
			.retrieve()
			.toBodilessEntity();

		S3Request deleteUploadedFileRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket).key("uploads/file.txt"))
			.build();
		restClient.delete()
			.uri(deleteUploadedFileRequest.uri())
			.headers(deleteUploadedFileRequest.headers())
			.retrieve()
			.toBodilessEntity();

		S3Request deleteBucketRequest = s3Request().endpoint(endpoint)
			.region(region)
			.accessKeyId(accessKeyId)
			.secretAccessKey(secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket))
			.build();
		restClient.delete()
			.uri(deleteBucketRequest.uri())
			.headers(deleteBucketRequest.headers())
			.retrieve()
			.toBodilessEntity();
	}

}
