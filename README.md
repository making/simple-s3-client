# simple-s3-client

A simple S3 Java Client that works with Spring `RestTemplate` or `RestClient`

This library provides two ways to interact with S3:
1. **Low-level API** using `S3Request` with `RestTemplate` or `RestClient`
2. **Fluent API** using `FluentS3Client` (Recommended for new projects)

```xml
		<dependency>
			<groupId>am.ik.s3</groupId>
			<artifactId>simple-s3-client</artifactId>
			<version>0.2.3</version>
		</dependency>
```

## Examples with `FluentS3Client` (Recommended)

The `FluentS3Client` provides a modern, fluent API for S3 operations with method chaining.

```java
// Create client with default RestClient configuration
FluentS3Client client = FluentS3Client.builder()
    .endpoint("https://s3.amazonaws.com")
    .region("us-east-1")
    .credentials("accessKeyId", "secretAccessKey")
    .build();

// Or use custom RestClient
RestClient customRestClient = RestClient.builder()
    .messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
    .build();

FluentS3Client client = FluentS3Client.builder()
    .endpoint("https://s3.amazonaws.com")
    .region("us-east-1")
    .credentials("accessKeyId", "secretAccessKey")
    .restClient(customRestClient)
    .build();

// Bucket operations
client.bucket("my-bucket").create();
ListBucketsResult buckets = client.listBuckets();
System.out.println(buckets);
ListBucketResult objects = client.bucket("my-bucket").listObjects();
System.out.println(objects);

// Object operations
client.bucket("my-bucket").object("hello.txt").put("Hello World!");
client.bucket("my-bucket").object("test.png").put(imageBytes, MediaType.IMAGE_PNG);

String content = client.bucket("my-bucket").object("hello.txt").get();
System.out.println("Content: " + content); // Content: Hello World!

byte[] imageData = client.bucket("my-bucket").object("test.png").getAsBytes();

// Clean up
client.bucket("my-bucket").object("hello.txt").delete();
client.bucket("my-bucket").object("test.png").delete();
client.bucket("my-bucket").delete();
```

## Examples with `RestTemplate` (Low-level API)

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

## Examples with `RestClient` (Low-level API)

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

## API Overview

### FluentS3Client

#### Client Creation
- `FluentS3Client.builder()` - Start building a client
- `.endpoint(String)` - Set S3 endpoint URL
- `.region(String)` - Set AWS region
- `.credentials(String accessKeyId, String secretAccessKey)` - Set AWS credentials
- `.restClient(RestClient)` - Use custom RestClient (optional)
- `.build()` - Create the client

#### Bucket Operations
- `client.listBuckets()` - List all buckets
- `client.bucket(String name)` - Select a bucket for operations
- `.create()` - Create the bucket
- `.delete()` - Delete the bucket
- `.listObjects()` - List objects in the bucket

#### Object Operations
- `client.bucket(String).object(String key)` - Select an object for operations
- `.put(String content)` - Upload string content as text/plain
- `.put(String content, MediaType mediaType)` - Upload string with specific media type
- `.put(byte[] content)` - Upload binary content as application/octet-stream
- `.put(byte[] content, MediaType mediaType)` - Upload binary with specific media type
- `.get()` - Download as string
- `.getAsBytes()` - Download as byte array
- `.delete()` - Delete the object

### When to Use Which API

**Use FluentS3Client when:**
- Building new applications
- You want a modern, intuitive API
- You prefer method chaining
- You need type safety and compile-time validation

**Use S3Request (low-level API) when:**
- You need fine-grained control over HTTP requests
- Working with existing code that uses S3Request
- Building custom abstractions on top of the library
- You need to access advanced S3 features not covered by FluentS3Client
