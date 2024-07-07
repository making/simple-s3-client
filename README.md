# simple-s3-client

A simple S3 Java Client that works with Spring `RestTemplate` or `RestClient`

```xml
		<dependency>
			<groupId>am.ik.s3</groupId>
			<artifactId>simple-s3-client</artifactId>
			<version>0.2.2</version>
		</dependency>
```

## Examples with `RestTemplate`

Make sure the `RestTemplate` has `MappingJackson2XmlHttpMessageConverter` to convert XML responses.


```java
import static am.ik.s3.S3RequestBuilder.s3Request;

URI endpoint = URI.create("https://...");
String region = "...";
String accessKeyId = "...";
String secretAccessKey = "...";
String bucket = "...";
RestTemplate restTempalte = ...;

// Put a bucket
S3Request putBucketRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket))
	.build();
restTemplate.exchange(putBucketRequest.toEntityBuilder().build(), Void.class);

// List buckets
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

// Put an object
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

// List a bucket
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

// Get an object
S3Request getObjectRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build();
String response = restTemplate.exchange(getObjectRequest.toEntityBuilder().build(), String.class).getBody();
System.out.println("Response: " + response); // Response: Hello World!

// Delete an object
S3Request deleteObjectRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.DELETE)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build();
restTemplate.exchange(deleteObjectRequest.toEntityBuilder().build(), Void.class);

// Delete a bucket
S3Request deleteBucketRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.DELETE)
	.path(b -> b.bucket(bucket))
	.build();
restTemplate.exchange(deleteBucketRequest.toEntityBuilder().build(), Void.class);
```

## Examples with `RestClient`

Make sure the `RestClient` has `MappingJackson2XmlHttpMessageConverter` to convert XML responses.

```java
import static am.ik.s3.S3RequestBuilder.s3Request;

URI endpoint = URI.create("https://...");
String region = "...";
String accessKeyId = "...";
String secretAccessKey = "...";
String bucket = "...";
RestClient restClient = ...;

// Put a bucket
S3Request putBucketRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket))
	.build();
restClient.put().uri(putBucketRequest.uri()).headers(putBucketRequest.headers()).retrieve().toBodilessEntity();

// List buckets
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

// Put an object
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

// List a bucket
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

// Get an object
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

// Delete an object
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

// Delete a bucket
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
```
