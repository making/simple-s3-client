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

String content = client.bucket("my-bucket").object("hello.txt").getAsString();
System.out.println("Content: " + content); // Content: Hello World!

byte[] imageData = client.bucket("my-bucket").object("test.png").getAsBytes();

// Streaming operations for large files
Resource resource = new PathResource("large-file.dat");
client.bucket("my-bucket")
    .object("large-file.dat")
    .putResource(resource);

// Download large files as Resource
Resource downloadResource = client.bucket("my-bucket")
    .object("large-file.dat")
    .getAsResource();
try (InputStream downloadStream = downloadResource.getInputStream()) {
    // Process stream without loading entire file into memory
    Files.copy(downloadStream, Paths.get("downloaded-file.dat"));
}

// Range requests for partial content
Resource partialResource = client.bucket("my-bucket")
    .object("large-file.dat")
    .range(1024, 2048)
    .getAsResource();
try (InputStream partialStream = partialResource.getInputStream()) {
    // Download only bytes 1024-2048
    byte[] partialData = partialStream.readAllBytes();
}

// Generate presigned URLs for secure access
PresignedUrl getUrl = client.bucket("my-bucket")
    .object("hello.txt")
    .presignedUrl(HttpMethod.GET, Duration.ofHours(2));
System.out.println("Get URL: " + getUrl.url());

// Presigned URL with additional headers
Map<String, String> headers = Map.of("Content-Type", "text/plain");
PresignedUrl putUrl = client.bucket("my-bucket")
    .object("upload.txt")
    .presignedUrl(HttpMethod.PUT, Duration.ofMinutes(15), headers);
System.out.println("Put URL: " + putUrl.url());

// Use presigned URL with RestClient
restClient.put()
    .uri(putUrl.url())
    .headers(putUrl.headers())
    .body("File content")
    .retrieve()
    .toBodilessEntity();

// Generate presigned POST form for browser uploads
PresignedPostForm postForm = client.bucket("my-bucket")
    .object("uploads/file.txt")
    .presignedPostForm(Duration.ofHours(1))
    .maxFileSize(DataSize.ofMegabytes(10))
    .addField("Content-Type", "text/plain")
    .generate();
System.out.println("POST URL: " + postForm.url());
System.out.println("Form fields: " + postForm.formFields());

// Using presigned URL in HTML form
// <form action="${postForm.url()}" method="post" enctype="multipart/form-data">
//   ${postForm.formFields().entrySet().stream()
//     .map(e -> "<input type='hidden' name='" + e.getKey() + "' value='" + e.getValue() + "'>")
//     .collect(Collectors.joining("\n"))}
//   <input type="file" name="file" required>
//   <button type="submit">Upload</button>
// </form>

// Multipart upload for large files
byte[] largeFileData = createLargeFile(); // 50MB file
CompleteMultipartUploadResult uploadResult = client.bucket("my-bucket")
    .object("large-file.zip")
    .multipartUpload()
    .partSize(DataSize.ofMegabytes(10))
    .maxConcurrentUploads(3)
    .progressCallback(ProgressCallback.logging())
    .upload(largeFileData);
System.out.println("Large file uploaded: " + uploadResult.etag());

// Multipart upload with Resource
Resource resource = new PathResource("large-file.dat");
CompletableFuture<CompleteMultipartUploadResult> future = client.bucket("my-bucket")
    .object("async-large-file.dat")
    .multipartUpload()
    .partSize(DataSize.ofMegabytes(5))
    .maxConcurrentUploads(5)
    .uploadAsync(resource);

// Continue with other work...
CompleteMultipartUploadResult result = future.get(); // Wait for completion
System.out.println("Async upload completed: " + result.etag());

// Bucket versioning configuration
// Enable versioning for a bucket
client.bucket("my-bucket").enableVersioning();

// Check versioning status
VersioningConfiguration config = client.bucket("my-bucket").getVersioningConfiguration();
System.out.println("Versioning enabled: " + config.isEnabled());

// Suspend versioning
client.bucket("my-bucket").suspendVersioning();

// Set versioning configuration directly
client.bucket("my-bucket").setVersioningConfiguration(VersioningConfiguration.enabled());

// Object versioning operations
// List all versions of an object
ListVersionsResult versions = client.bucket("my-bucket")
    .object("hello.txt")
    .listVersions();
System.out.println("Found " + versions.versions().size() + " versions");

// Get a specific version
String versionId = versions.versions().get(0).versionId();
String versionedContent = client.bucket("my-bucket")
    .object("hello.txt")
    .version(versionId)
    .getAsString();

// Delete a specific version
client.bucket("my-bucket")
    .object("hello.txt")
    .version(versionId)
    .delete();

// Copy a specific version to a new object
client.bucket("my-bucket")
    .object("hello.txt")
    .version(versionId)
    .copyTo("my-bucket", "hello-backup.txt");

// Generate presigned URL for a specific version
PresignedUrl versionUrl = client.bucket("my-bucket")
    .object("hello.txt")
    .version(versionId)
    .presignedUrl(HttpMethod.GET, Duration.ofHours(1));

// List all object versions in a bucket
ListVersionsResult bucketVersions = client.bucket("my-bucket")
    .listVersions();
bucketVersions.versions().forEach(version -> {
    System.out.println(version.key() + " - " + version.versionId() + 
        " (Latest: " + version.isLatest() + ")");
});
bucketVersions.deleteMarkers().forEach(marker -> {
    System.out.println(marker.key() + " - DELETE MARKER - " + marker.versionId());
});

// Clean up
client.bucket("my-bucket").object("hello.txt").delete();
client.bucket("my-bucket").object("test.png").delete();
client.bucket("my-bucket").object("large-file.zip").delete();
client.bucket("my-bucket").object("async-large-file.dat").delete();
client.bucket("my-bucket").delete();
```

## Examples with `RestTemplate` (Low-level API)

Make sure the `RestTemplate` has `MappingJackson2XmlHttpMessageConverter` to convert XML responses.


```java
import static am.ik.s3.S3RequestBuilder.s3Request;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

URI endpoint = URI.create("https://...");
String region = "...";
String accessKeyId = "...";
String secretAccessKey = "...";
String bucket = "...";
RestTemplate restTemplate = ...;

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

PresignedUrl presignedUrl = getRequest.presignedUrl(Duration.ofHours(1));
System.out.println("Presigned URL: " + presignedUrl.url());

// Use the presigned URL with RestTemplate
HttpHeaders httpHeaders = new HttpHeaders();
presignedUrl.requiredHeaders().forEach(httpHeaders::add);
HttpEntity<Void> httpEntity = new HttpEntity<>(httpHeaders);
String content = restTemplate.exchange(presignedUrl.url(), HttpMethod.GET, httpEntity, String.class).getBody();

// Generate presigned POST form for browser uploads
S3Request postRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.POST)  // Note: Use POST for S3Request
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
HttpHeaders postHeaders = new HttpHeaders();
postHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
HttpEntity<MultiValueMap<String, Object>> postEntity = new HttpEntity<>(formData, postHeaders);
restTemplate.postForEntity(postForm.url(), postEntity, Void.class);
System.out.println("File uploaded successfully via presigned POST form");

// Verify the uploaded file
S3Request verifyRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("uploads/file.txt"))
	.build();
String uploadedContent = restTemplate.exchange(verifyRequest.toEntityBuilder().build(), String.class).getBody();
System.out.println("Uploaded content: " + uploadedContent);

// Object versioning with RestTemplate
// List object versions
S3Request listVersionsRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket))
	.build()
	.listVersions();
ListVersionsResult versionsResult = restTemplate
	.exchange(listVersionsRequest.toEntityBuilder().build(), ListVersionsResult.class)
	.getBody();
System.out.println("Found " + versionsResult.versions().size() + " versions");

// Get specific version
String versionId = versionsResult.versions().get(0).versionId();
S3Request getVersionRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build()
	.withVersionId(versionId);
String versionedContent = restTemplate
	.exchange(getVersionRequest.toEntityBuilder().build(), String.class)
	.getBody();

// Delete specific version
S3Request deleteVersionRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.DELETE)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build()
	.deleteVersion(versionId);
restTemplate.exchange(deleteVersionRequest.toEntityBuilder().build(), Void.class);

// Multipart upload example with RestTemplate
byte[] largeData = new byte[15 * 1024 * 1024]; // 15MB file
Arrays.fill(largeData, (byte) 'A');

S3Request baseRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket).key("large-file.dat"))
	.build();

// Step 1: Initiate multipart upload
S3Request initiateRequest = baseRequest.initiateMultipartUpload();
InitiateMultipartUploadResult initResult = restTemplate.exchange(
	initiateRequest.toEntityBuilder().build(), 
	InitiateMultipartUploadResult.class).getBody();
String uploadId = initResult.uploadId();

try {
	// Step 2: Upload parts
	List<CompletedPart> completedParts = new ArrayList<>();
	int partSize = 5 * 1024 * 1024; // 5MB per part
	int partNumber = 1;
	
	for (int offset = 0; offset < largeData.length; offset += partSize) {
		int currentPartSize = Math.min(partSize, largeData.length - offset);
		byte[] partData = Arrays.copyOfRange(largeData, offset, offset + currentPartSize);
		
		S3Request uploadPartRequest = baseRequest.uploadPart(uploadId, partNumber, partData);
		ResponseEntity<Void> partResponse = restTemplate.exchange(
			uploadPartRequest.toEntityBuilder().body(partData), Void.class);
		
		String etag = partResponse.getHeaders().getFirst("ETag");
		if (etag.startsWith("\"") && etag.endsWith("\"")) {
			etag = etag.substring(1, etag.length() - 1);
		}
		completedParts.add(new CompletedPart(etag, partNumber));
		partNumber++;
	}
	
	// Step 3: Complete multipart upload
	CompleteMultipartUpload completeRequest = new CompleteMultipartUpload(completedParts);
	S3Request completeUploadRequest = baseRequest.completeMultipartUpload(uploadId, completeRequest);
	CompleteMultipartUploadResult result = restTemplate.exchange(
		completeUploadRequest.toEntityBuilder().body(completeRequest), 
		CompleteMultipartUploadResult.class).getBody();
	
	System.out.println("Multipart upload completed: " + result.etag());
	
} catch (Exception e) {
	// Abort multipart upload on error
	S3Request abortRequest = baseRequest.abortMultipartUpload(uploadId);
	restTemplate.exchange(abortRequest.toEntityBuilder().build(), Void.class);
	throw new RuntimeException("Multipart upload failed", e);
}
```

## Examples with `RestClient` (Low-level API)

Make sure the `RestClient` has `MappingJackson2XmlHttpMessageConverter` to convert XML responses.

```java
import static am.ik.s3.S3RequestBuilder.s3Request;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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

// Streaming GET with range request
S3Request rangeRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("large-file.dat"))
	.build()
	.withRange(1024, 2048);

Resource rangeResource = restClient.get()
	.uri(rangeRequest.uri())
	.headers(rangeRequest.headers())
	.retrieve()
	.body(Resource.class);
try (InputStream rangeStream = rangeResource.getInputStream()) {
	// Process partial content stream
	byte[] partialData = rangeStream.readAllBytes();
}

// Streaming PUT with Resource from file using S3Content.ofResource
Resource fileResource = new PathResource("large-file.dat");
S3Request putStreamRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket).key("large-file.dat"))
	.content(S3Content.ofResource(fileResource, MediaType.APPLICATION_OCTET_STREAM))
	.build();

restClient.put()
	.uri(putStreamRequest.uri())
	.headers(putStreamRequest.headers())
	.body(fileResource)
	.retrieve()
	.toBodilessEntity();

// Streaming PUT with Resource from byte array using S3Content.ofResource
byte[] data = "This is test data for streaming upload".getBytes(StandardCharsets.UTF_8);
Resource byteResource = new ByteArrayResource(data);
S3Request putByteStreamRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket).key("stream-test.txt"))
	.content(S3Content.ofResource(byteResource, MediaType.TEXT_PLAIN))
	.build();

restClient.put()
	.uri(putByteStreamRequest.uri())
	.headers(putByteStreamRequest.headers())
	.body(byteResource)
	.retrieve()
	.toBodilessEntity();

// Streaming GET as Resource
S3Request getStreamRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("large-file.dat"))
	.build();

Resource downloadResource = restClient.get()
	.uri(getStreamRequest.uri())
	.headers(getStreamRequest.headers())
	.retrieve()
	.body(Resource.class);
try (InputStream downloadStream = downloadResource.getInputStream()) {
	// Process downloaded stream without loading entire file into memory
	Files.copy(downloadStream, Paths.get("downloaded-file.dat"));
}

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
	.method(HttpMethod.POST)  // Note: Use POST for S3Request
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

// Object versioning with RestClient
// List object versions
S3Request listVersionsRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket))
	.build()
	.listVersions();
ListVersionsResult versionsResult = restClient.get()
	.uri(listVersionsRequest.uri())
	.headers(listVersionsRequest.headers())
	.retrieve()
	.body(ListVersionsResult.class);
System.out.println("Found " + versionsResult.versions().size() + " versions");

// Get specific version
String versionId = versionsResult.versions().get(0).versionId();
S3Request getVersionRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.GET)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build()
	.withVersionId(versionId);
String versionedContent = restClient.get()
	.uri(getVersionRequest.uri())
	.headers(getVersionRequest.headers())
	.retrieve()
	.body(String.class);

// Delete specific version
S3Request deleteVersionRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.DELETE)
	.path(b -> b.bucket(bucket).key("hello.txt"))
	.build()
	.deleteVersion(versionId);
restClient.delete()
	.uri(deleteVersionRequest.uri())
	.headers(deleteVersionRequest.headers())
	.retrieve()
	.toBodilessEntity();

// Multipart upload example with RestClient
byte[] largeData = new byte[15 * 1024 * 1024]; // 15MB file
Arrays.fill(largeData, (byte) 'A');

S3Request baseRequest = s3Request().endpoint(endpoint)
	.region(region)
	.accessKeyId(accessKeyId)
	.secretAccessKey(secretAccessKey)
	.method(HttpMethod.PUT)
	.path(b -> b.bucket(bucket).key("large-file.dat"))
	.build();

// Step 1: Initiate multipart upload
S3Request initiateRequest = baseRequest.initiateMultipartUpload();
InitiateMultipartUploadResult initResult = restClient.post()
	.uri(initiateRequest.uri())
	.headers(initiateRequest.headers())
	.retrieve()
	.body(InitiateMultipartUploadResult.class);
String uploadId = initResult.uploadId();

try {
	// Step 2: Upload parts
	List<CompletedPart> completedParts = new ArrayList<>();
	int partSize = 5 * 1024 * 1024; // 5MB per part
	int partNumber = 1;
	
	for (int offset = 0; offset < largeData.length; offset += partSize) {
		int currentPartSize = Math.min(partSize, largeData.length - offset);
		byte[] partData = Arrays.copyOfRange(largeData, offset, offset + currentPartSize);
		
		S3Request uploadPartRequest = baseRequest.uploadPart(uploadId, partNumber, partData);
		var partResponse = restClient.put()
			.uri(uploadPartRequest.uri())
			.headers(uploadPartRequest.headers())
			.body(partData)
			.retrieve()
			.toBodilessEntity();
		
		String etag = partResponse.getHeaders().getFirst("ETag");
		if (etag.startsWith("\"") && etag.endsWith("\"")) {
			etag = etag.substring(1, etag.length() - 1);
		}
		completedParts.add(new CompletedPart(etag, partNumber));
		partNumber++;
	}
	
	// Step 3: Complete multipart upload
	CompleteMultipartUpload completeRequest = new CompleteMultipartUpload(completedParts);
	S3Request completeUploadRequest = baseRequest.completeMultipartUpload(uploadId, completeRequest);
	CompleteMultipartUploadResult result = restClient.post()
		.uri(completeUploadRequest.uri())
		.headers(completeUploadRequest.headers())
		.body(completeRequest)
		.retrieve()
		.body(CompleteMultipartUploadResult.class);
	
	System.out.println("Multipart upload completed: " + result.etag());
	
} catch (Exception e) {
	// Abort multipart upload on error
	S3Request abortRequest = baseRequest.abortMultipartUpload(uploadId);
	restClient.delete()
		.uri(abortRequest.uri())
		.headers(abortRequest.headers())
		.retrieve()
		.toBodilessEntity();
	throw new RuntimeException("Multipart upload failed", e);
}
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
- `.putResource(Resource)` - Upload from Spring Resource
- `.putResource(Resource, MediaType)` - Upload from Spring Resource with specific media type
- `.getAsString()` - Download as string
- `.getAsBytes()` - Download as byte array
- `.getAsResource()` - Download as Spring Resource for streaming
- `.delete()` - Delete the object

#### Presigned URL Operations
- `.presignedUrl(HttpMethod)` - Generate presigned URL with default 1 hour expiration
- `.presignedUrl(HttpMethod, Duration)` - Generate presigned URL with custom expiration
- `.presignedUrl(HttpMethod, Duration, Map<String, String>)` - Generate presigned URL with additional headers

#### Presigned POST Form Operations
- `.presignedPostForm()` - Create a presigned POST form generator with default 1 hour expiration
- `.presignedPostForm(Duration)` - Create a presigned POST form generator with custom expiration
- `.maxFileSize(long)` - Set maximum file size in bytes
- `.maxFileSize(DataSize)` - Set maximum file size using DataSize
- `.addField(String, String)` - Add a form field
- `.addFields(Map<String, String>)` - Add multiple form fields
- `.addCondition(String)` - Add a policy condition
- `.addConditions(List<String>)` - Add multiple policy conditions
- `.generate()` - Generate the presigned POST form

#### Multipart Upload Operations
- `.multipartUpload()` - Create a multipart upload builder for large files
- `.partSize(DataSize)` - Set the part size (minimum 5MB, maximum 5GB)
- `.maxConcurrentUploads(int)` - Set maximum number of concurrent part uploads
- `.progressCallback(ProgressCallback)` - Set progress tracking callback
- `.configuration(MultipartUploadConfiguration)` - Set custom configuration
- `.upload(byte[])` - Upload data using multipart upload (synchronous)
- `.upload(Resource)` - Upload from Spring Resource (synchronous)
- `.uploadAsync(byte[])` - Upload data asynchronously
- `.uploadAsync(Resource)` - Upload from Spring Resource asynchronously

#### Range Request Operations
- `.range(long start, long end)` - Create range request builder for partial content retrieval
- `.rangeFrom(long start)` - Create range request builder from specific position to end
- Range request builder methods:
  - `.getAsResource()` - Download partial content as Spring Resource
  - `.getAsBytes()` - Download partial content as byte array
  - `.getAsString()` - Download partial content as string

#### Versioning Operations
- `.listVersions()` - List all object versions in a bucket
- `.listVersions(prefix, keyMarker, versionIdMarker, maxKeys)` - List versions with parameters
- `.object(String).listVersions()` - List all versions of a specific object
- `.object(String).version(String versionId)` - Select a specific version for operations
- Version operation builder methods:
  - `.getAsString()` - Download specific version as string
  - `.getAsBytes()` - Download specific version as byte array
  - `.getAsResource()` - Download specific version as Spring Resource
  - `.delete()` - Delete specific version
  - `.copyTo(String bucket, String key)` - Copy specific version to new location
  - `.presignedUrl(HttpMethod, Duration)` - Generate presigned URL for specific version


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
