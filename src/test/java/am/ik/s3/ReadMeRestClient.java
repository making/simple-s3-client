package am.ik.s3;

import java.net.URI;
import java.util.UUID;

import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static am.ik.s3.S3RequestBuilder.s3Request;

public class ReadMeRestClient {

	public static void main(String[] args) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors()
			.add(new LogbookClientHttpRequestInterceptor(Logbook.builder().headerFilter(headers -> headers).build()));
		RestClient restClient = RestClient.builder()
			.requestInterceptor(
					new LogbookClientHttpRequestInterceptor(Logbook.builder().headerFilter(headers -> headers).build()))
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
