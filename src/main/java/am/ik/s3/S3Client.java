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
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class S3Client {

	private final RestTemplate restTemplate;

	private final URI endpoint;

	private final String region;

	private final String accessKeyId;

	private final String secretAccessKey;

	public static final String AWS4_HMAC_SHA256 = "AWS4-HMAC-SHA256";

	private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

	private Clock clock = Clock.systemUTC();

	public S3Client(RestTemplate restTemplate, URI endpoint, String region, String accessKeyId,
			String secretAccessKey) {
		this.restTemplate = restTemplate;
		this.endpoint = endpoint;
		this.region = region;
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
	}

	public ListBucketsResult listBuckets() {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.GET, "/", "");
		return this.restTemplate.exchange(request, ListBucketsResult.class).getBody();
	}

	public ListBucketResult listBucket(String bucketName) {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.GET, "/%s".formatted(bucketName), "");
		return this.restTemplate.exchange(request, ListBucketResult.class).getBody();
	}

	public void deleteBucket(String bucketName) {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.DELETE, "/%s".formatted(bucketName), "");
		this.restTemplate.exchange(request, String.class);
	}

	public void putBucket(String bucketName) {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.PUT, "/%s".formatted(bucketName), "");
		this.restTemplate.exchange(request, String.class);
	}

	public void putObject(String bucketName, String key, byte[] content, MediaType mediaType) {
		RequestEntity<byte[]> request = this.requestEntity(HttpMethod.PUT,
				"/%s/%s".formatted(bucketName, encodePath(key)), "", content, mediaType);
		this.restTemplate.exchange(request, Void.class);
	}

	public void putObject(String bucketName, String key, Resource resource, MediaType mediaType) {
		try {
			RequestEntity<byte[]> request = this.requestEntity(HttpMethod.PUT,
					"/%s/%s".formatted(bucketName, encodePath(key)), "", resource.getContentAsByteArray(), mediaType);
			this.restTemplate.exchange(request, Void.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public byte[] getObject(String bucketName, String key) {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.GET,
				"/%s/%s".formatted(bucketName, encodePath(key)), "");
		return this.restTemplate.exchange(request, byte[].class).getBody();
	}

	public void deleteObject(String bucketName, String key) {
		RequestEntity<Void> request = this.requestEntity(HttpMethod.DELETE,
				"/%s/%s".formatted(bucketName, encodePath(key)), "");
		this.restTemplate.exchange(request, Void.class);
	}

	private RequestEntity<Void> requestEntity(HttpMethod method, String canonicalUri, String canonicalQueryString) {
		return this.requestBuilder(method, canonicalUri, canonicalQueryString, null, null).build();
	}

	private RequestEntity<byte[]> requestEntity(HttpMethod method, String canonicalUri, String canonicalQueryString,
			byte[] content, MediaType mediaType) {
		return this.requestBuilder(method, canonicalUri, canonicalQueryString, content, mediaType).body(content);
	}

	private RequestEntity.BodyBuilder requestBuilder(HttpMethod method, String canonicalUri,
			String canonicalQueryString, byte[] content, MediaType mediaType) {
		AmzDate amzDate = new AmzDate(this.clock.instant());
		String contentSha256 = content == null ? UNSIGNED_PAYLOAD : encodeHex(sha256Hash(content));
		TreeMap<String, String> headers = new TreeMap<>();
		headers.put(HttpHeaders.HOST, this.host());
		headers.put(AmzHttpHeaders.X_AMZ_CONTENT_SHA256, contentSha256);
		headers.put(AmzHttpHeaders.X_AMZ_DATE, amzDate.date());
		if (content != null) {
			headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length));
		}
		if (mediaType != null) {
			headers.put(HttpHeaders.CONTENT_TYPE, mediaType.toString());
		}
		String authorization = this.authorization(method, canonicalUri, canonicalQueryString, headers, contentSha256,
				amzDate);
		HttpHeaders httpHeaders = new HttpHeaders();
		headers.forEach(httpHeaders::add);
		httpHeaders.add(HttpHeaders.AUTHORIZATION, authorization);
		URI uri = UriComponentsBuilder.fromUri(this.endpoint)
			.path(canonicalUri)
			.query(canonicalQueryString)
			.build(true)
			.toUri();
		return RequestEntity.method(method, uri).headers(httpHeaders);
	}

	private static String encodePath(String path) {
		String encodedPath = UriComponentsBuilder.fromPath(path).encode().build().getPath();
		if (encodedPath == null) {
			return null;
		}
		return encodedPath.replace("!", "%21")
			.replace("#", "%23")
			.replace("$", "%24")
			.replace("%", "%25")
			.replace("&", "%26")
			.replace("'", "%27")
			.replace("(", "%28")
			.replace(")", "%29")
			.replace("*", "%2A")
			.replace("+", "%2B")
			.replace(",", "%2C")
			.replace(":", "%3A")
			.replace(";", "%3B")
			.replace("=", "%3D")
			.replace("@", "%40")
			.replace("[", "%5B")
			.replace("]", "%5D")
			.replace("{", "%7B")
			.replace("}", "%7D");
	}

	private String authorization(HttpMethod method, String canonicalUri, String canonicalQueryString,
			TreeMap<String, String> headers /* must appear in alphabetical order */, String payloadHash,
			AmzDate amzDate) {
		// Step 1: Create a canonical request
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-canonical-request
		String canonicalHeaders = headers.entrySet()
			.stream()
			.map(e -> "%s:%s".formatted(e.getKey().toLowerCase(), e.getValue()))
			.collect(Collectors.joining("\n")) + "\n";
		String signedHeaders = headers.keySet().stream().map(String::toLowerCase).collect(Collectors.joining(";"));
		String canonicalRequest = String.join("\n", method.name(), canonicalUri, canonicalQueryString, canonicalHeaders,
				signedHeaders, payloadHash);
		// Step 2: Create a hash of the canonical request
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-canonical-request-hash
		String hashedCanonicalRequest = encodeHex(sha256Hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
		// Step 3: Create a string to sign
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-string-to-sign
		String credentialScope = "%s/%s/s3/aws4_request".formatted(amzDate.yymmdd(), this.region);
		String stringToSign = String.join("\n", AWS4_HMAC_SHA256, amzDate.date(), credentialScope,
				hashedCanonicalRequest);
		// Step 4: Calculate the signature
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#calculate-signature
		String signature = this.sign(stringToSign, amzDate);
		// Step 5: Add the signature to the request
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#add-signature-to-request
		String credential = "%s/%s".formatted(this.accessKeyId, credentialScope);
		return "%s Credential=%s,SignedHeaders=%s,Signature=%s".formatted(AWS4_HMAC_SHA256, credential, signedHeaders,
				signature);
	}

	private String sign(String stringToSign, AmzDate amzDate) {
		byte[] kSecret = ("AWS4" + this.secretAccessKey).getBytes(StandardCharsets.UTF_8);
		byte[] kDate = hmacSHA256(amzDate.yymmdd(), kSecret);
		byte[] kRegion = hmacSHA256(this.region, kDate);
		byte[] kService = hmacSHA256("s3", kRegion);
		byte[] kSigning = hmacSHA256("aws4_request", kService);
		return encodeHex(hmacSHA256(stringToSign, kSigning));
	}

	private String host() {
		return this.endpoint.toString().replace("https://", "").replace("http://", "");
	}

	public void setClock(Clock clock) {
		this.clock = clock;
	}

	private static byte[] sha256Hash(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return md.digest(data);
		}
		catch (NoSuchAlgorithmException e) {
			// should not happen
			throw new IllegalStateException(e);
		}
	}

	private static byte[] hmacSHA256(String data, byte[] key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			// should not happen
			throw new IllegalStateException(e);
		}
	}

	private static String encodeHex(byte[] data) {
		HexFormat hex = HexFormat.of();
		StringBuilder sb = new StringBuilder();
		for (byte datum : data) {
			sb.append(hex.toHexDigits(datum));
		}
		return sb.toString();
	}

}
