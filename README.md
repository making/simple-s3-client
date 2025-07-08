# simple-s3-client

A simple S3 Java Client that works with Spring `RestTemplate` or `RestClient`

This library provides two ways to interact with S3:
1. **Low-level API** using `S3Request` with `RestTemplate` or `RestClient`
2. **Fluent API** using `S3Client` (Recommended for new projects)

```xml
		<dependency>
			<groupId>am.ik.s3</groupId>
			<artifactId>simple-s3-client</artifactId>
			<version>0.2.3</version>
		</dependency>
```

## Examples with `S3Client` (Recommended)

The `S3Client` provides a modern, fluent API for S3 operations with method chaining.

```java
import java.time.Duration;
import am.ik.s3.*;

// Create client with default RestClient configuration
S3Client client = S3Client.builder()
    .endpoint("https://s3.amazonaws.com")
    .region("us-east-1")
    .credentials("accessKeyId", "secretAccessKey")
    .build();

// Or use custom RestClient
RestClient customRestClient = RestClient.builder()
    .messageConverters(converters -> converters.add(new MappingJackson2XmlHttpMessageConverter()))
    .build();

S3Client client = S3Client.builder()
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

// Generate presigned URLs for secure access
PresignedUrl getUrl = client.bucket("my-bucket")
    .object("hello.txt")
    .presignedUrl()
    .expiration(Duration.ofHours(2))
    .forGet();
System.out.println("Get URL: " + getUrl.url());

PresignedUrl putUrl = client.bucket("my-bucket")
    .object("upload.txt")
    .presignedUrl()
    .expiration(Duration.ofMinutes(15))
    .contentType("text/plain")
    .forPut();
System.out.println("Put URL: " + putUrl.url());

// Generate presigned POST form for browser uploads
PresignedPostForm postForm = client.bucket("my-bucket")
    .object("uploads/${filename}")
    .presignedPostForm()
    .expiration(Duration.ofHours(1))
    .maxFileSize(DataSize.ofMegabytes(10))
    .generate();
System.out.println("POST URL: " + postForm.url());
System.out.println("Form fields: " + postForm.formFields());

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

// Generate presigned URL for GET operation
S3Request getRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build();

PresignedUrl presignedUrl = getRequest.generatePresignedUrl(Duration.ofHours(1));
System.out.println("Presigned URL: " + presignedUrl.url());

// Use the presigned URL with RestTemplate
HttpHeaders httpHeaders = new HttpHeaders();
presignedUrl.requiredHeaders().forEach(httpHeaders::add);
HttpEntity<Void> httpEntity = new HttpEntity<>(httpHeaders);
String content = restTemplate.exchange(presignedUrl.url(), HttpMethod.GET, httpEntity, String.class).getBody();

// Generate presigned POST form for browser uploads
S3ClientConfiguration config = new S3ClientConfiguration(endpoint, region, accessKeyId, secretAccessKey);
PresignedPostForm postForm = PresignedPostForm.builder(config, bucket, "uploads/file.txt")
	.expiration(Duration.ofHours(1))
	.maxFileSize(DataSize.ofMegabytes(10))
	.field("Content-Type", "text/plain")
	.generate();

System.out.println("POST URL: " + postForm.url());
System.out.println("Form fields: " + postForm.formFields());
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

// Generate presigned URL for GET operation
S3Request getRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build();

PresignedUrl presignedUrl = getRequest.generatePresignedUrl(Duration.ofHours(1));
System.out.println("Presigned URL: " + presignedUrl.url());

// Use the presigned URL
String content = restClient.get()
	.uri(presignedUrl.url())
	.headers(presignedUrl.headers())
	.retrieve()
	.body(String.class);

// Generate presigned POST form for browser uploads
S3ClientConfiguration config = new S3ClientConfiguration(endpoint, region, accessKeyId, secretAccessKey);
PresignedPostForm postForm = PresignedPostForm.builder(config, bucket, "uploads/file.txt")
	.expiration(Duration.ofHours(1))
	.maxFileSize(DataSize.ofMegabytes(10))
	.field("Content-Type", "text/plain")
	.generate();

System.out.println("POST URL: " + postForm.url());
System.out.println("Form fields: " + postForm.formFields());
```

## API Overview

### S3Client

#### Client Creation
- `S3Client.builder()` - Start building a client
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

#### Presigned URL Operations
- `.presignedUrl()` - Create a presigned URL builder
- `.expiration(Duration)` - Set expiration time for the URL
- `.contentType(String)` - Set content type for PUT operations
- `.header(String, String)` - Add custom headers
- `.parameter(String, String)` - Add query parameters
- `.forGet()` - Generate presigned URL for GET operation
- `.forPut()` - Generate presigned URL for PUT operation
- `.forDelete()` - Generate presigned URL for DELETE operation

#### Presigned POST Form Operations
- `.presignedPostForm()` - Create a presigned POST form builder
- `.expiration(Duration)` - Set expiration time for the form
- `.maxFileSize(DataSize)` - Set maximum file size in bytes
- `.field(String, String)` - Add form field
- `.condition(String)` - Add policy condition
- `.generate()` - Generate the presigned POST form

### When to Use Which API

**Use S3Client when:**
- Building new applications
- You want a modern, intuitive API
- You prefer method chaining
- You need type safety and compile-time validation

**Use S3Request (low-level API) when:**
- You need fine-grained control over HTTP requests
- Working with existing code that uses S3Request
- Building custom abstractions on top of the library
- You need to access advanced S3 features not covered by S3Client
