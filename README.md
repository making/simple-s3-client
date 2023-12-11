# simple-s3-client

A simple S3 Java Client that only uses RestTemplate and Jackson

```xml
		<dependency>
			<groupId>am.ik.s3</groupId>
			<artifactId>simple-s3-client</artifactId>
			<version>0.1.1</version>
		</dependency>
```


```java
S3Client s3Client = new S3Client(this.restTemplate, URI.create(endpoint), regionName, accessKeyId, secretAccessKey);

s3Client.putObject("test", "foo.txt", new ClassPathResource("foo.txt"), MediaType.TEXT_PLAIN);

byte[] content = s3Client.getObject("test", "foo.txt");
```