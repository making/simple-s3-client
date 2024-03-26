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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import static am.ik.s3.S3RequestBuilder.s3Request;

public class S3Client {

	private final RestTemplate restTemplate;

	private final URI endpoint;

	private final String region;

	private final String accessKeyId;

	private final String secretAccessKey;

	public S3Client(RestTemplate restTemplate, URI endpoint, String region, String accessKeyId,
			String secretAccessKey) {
		this.restTemplate = restTemplate;
		this.endpoint = endpoint;
		this.region = region;
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
	}

	public ListBucketsResult listBuckets() {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b)
			.build()
			.toEntityBuilder()
			.build();
		return this.restTemplate.exchange(request, ListBucketsResult.class).getBody();
	}

	public ListBucketResult listBucket(String bucket) {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket))
			.build()
			.toEntityBuilder()
			.build();
		return this.restTemplate.exchange(request, ListBucketResult.class).getBody();
	}

	public void deleteBucket(String bucket) {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket))
			.build()
			.toEntityBuilder()
			.build();
		this.restTemplate.exchange(request, String.class);
	}

	public void putBucket(String bucket) {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket))
			.build()
			.toEntityBuilder()
			.build();
		this.restTemplate.exchange(request, String.class);
	}

	public void putObject(String bucket, String key, byte[] content, MediaType mediaType) {
		RequestEntity<byte[]> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket).key(key))
			.content(S3Content.of(content, mediaType))
			.build()
			.toEntityBuilder()
			.body(content);
		this.restTemplate.exchange(request, Void.class);
	}

	public void putObject(String bucket, String key, Resource resource, MediaType mediaType) {
		try {
			byte[] body = resource.getContentAsByteArray();
			RequestEntity<byte[]> request = s3Request().endpoint(this.endpoint)
				.region(this.region)
				.accessKeyId(this.accessKeyId)
				.secretAccessKey(this.secretAccessKey)
				.method(HttpMethod.PUT)
				.path(b -> b.bucket(bucket).key(key))
				.content(S3Content.of(body, mediaType))
				.build()
				.toEntityBuilder()
				.body(body);
			this.restTemplate.exchange(request, Void.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public byte[] getObject(String bucket, String key) {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key(key))
			.build()
			.toEntityBuilder()
			.build();
		return this.restTemplate.exchange(request, byte[].class).getBody();
	}

	public void deleteObject(String bucket, String key) {
		RequestEntity<Void> request = s3Request().endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey)
			.method(HttpMethod.DELETE)
			.path(b -> b.bucket(bucket).key(key))
			.build()
			.toEntityBuilder()
			.build();
		this.restTemplate.exchange(request, Void.class);
	}

}
