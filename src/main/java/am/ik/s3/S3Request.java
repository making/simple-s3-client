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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jilt.Builder;
import org.jilt.BuilderStyle;
import org.jilt.Opt;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Represents an S3 request with AWS Signature Version 4 signing.
 */
public final class S3Request {

	private final URI endpoint;

	private final String region;

	private final String accessKeyId;

	private final String secretAccessKey;

	private final HttpMethod method;

	private final String canonicalUri;

	private final String canonicalQueryString;

	private final S3Content content;

	private final Clock clock;

	/**
	 * The AWS Signature Version 4 algorithm identifier.
	 */
	public static final String AWS4_HMAC_SHA256 = "AWS4-HMAC-SHA256";

	private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

	private URI uri;

	private HttpHeaders httpHeaders;

	/**
	 * Creates a new S3Request.
	 * @param endpoint the S3 endpoint URI
	 * @param region the AWS region
	 * @param accessKeyId the AWS access key ID
	 * @param secretAccessKey the AWS secret access key
	 * @param method the HTTP method for the request
	 * @param path the path builder function
	 * @param canonicalQueryString the canonical query string
	 * @param content the request content
	 * @param clock the clock to use for timestamps
	 */
	@Builder(style = BuilderStyle.STAGED)
	public S3Request(URI endpoint, String region, String accessKeyId, String secretAccessKey, HttpMethod method,
			Function<S3PathBuilder, S3PathBuilder> path, @Opt String canonicalQueryString, @Opt S3Content content,
			@Opt Clock clock) {
		this.endpoint = endpoint;
		this.region = region;
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.method = method;
		this.canonicalUri = path == null ? "/" : path.apply(new S3PathBuilder()).build().toCanonicalUri();
		this.canonicalQueryString = Objects.requireNonNullElse(canonicalQueryString, "");
		this.content = content;
		this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
		this.init();
	}

	private void init() {
		AmzDate amzDate = new AmzDate(this.clock.instant());
		String contentSha256 = content == null ? UNSIGNED_PAYLOAD
				: S3RequestSigningUtils.encodeHex(S3RequestSigningUtils.sha256Hash(content.body()));
		TreeMap<String, String> headers = new TreeMap<>();
		StringBuilder host = new StringBuilder(this.endpoint.getHost());
		if (this.endpoint.getPort() != -1) {
			host.append(":").append(this.endpoint.getPort());
		}
		headers.put(HttpHeaders.HOST, host.toString());
		headers.put(AmzHttpHeaders.X_AMZ_CONTENT_SHA256, contentSha256);
		headers.put(AmzHttpHeaders.X_AMZ_DATE, amzDate.date());
		if (content != null && content.body() != null) {
			headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.body().length));
		}
		if (content != null && content.mediaType() != null) {
			headers.put(HttpHeaders.CONTENT_TYPE, content.mediaType().toString());
		}
		String authorization = this.authorization(headers, contentSha256, amzDate);
		this.httpHeaders = new HttpHeaders();
		headers.forEach(this.httpHeaders::add);
		this.httpHeaders.add(HttpHeaders.AUTHORIZATION, authorization);
		this.uri = UriComponentsBuilder.fromUri(this.endpoint)
			.path(canonicalUri)
			.query(canonicalQueryString)
			.build(true)
			.toUri();
	}

	/**
	 * Returns the complete URI for this S3 request.
	 * @return the request URI
	 */
	public URI uri() {
		return this.uri;
	}

	/**
	 * Returns a consumer that adds the necessary headers to an HTTP request.
	 * @return a header consumer
	 */
	public Consumer<HttpHeaders> headers() {
		return headers -> headers.addAll(this.httpHeaders);
	}

	/**
	 * Converts this S3 request to a Spring RequestEntity.BodyBuilder.
	 * @return a RequestEntity.BodyBuilder with the method, URI, and headers set
	 */
	public RequestEntity.BodyBuilder toEntityBuilder() {
		return RequestEntity.method(this.method, this.uri).headers(this.httpHeaders);
	}

	private String authorization(
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
		String hashedCanonicalRequest = S3RequestSigningUtils
			.encodeHex(S3RequestSigningUtils.sha256Hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
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
		byte[] kDate = S3RequestSigningUtils.hmacSHA256(amzDate.yymmdd(), kSecret);
		byte[] kRegion = S3RequestSigningUtils.hmacSHA256(this.region, kDate);
		byte[] kService = S3RequestSigningUtils.hmacSHA256("s3", kRegion);
		byte[] kSigning = S3RequestSigningUtils.hmacSHA256("aws4_request", kService);
		return S3RequestSigningUtils.encodeHex(S3RequestSigningUtils.hmacSHA256(stringToSign, kSigning));
	}

	/**
	 * Generates a presigned URL for this S3 request.
	 * @param expiration the duration until the URL expires
	 * @return a PresignedUrl containing the URL and expiration information
	 * @since 0.3.0
	 */
	public PresignedUrl generatePresignedUrl(Duration expiration) {
		return generatePresignedUrl(expiration, null);
	}

	/**
	 * Generates a presigned URL for this S3 request with additional headers.
	 * @param expiration the duration until the URL expires
	 * @param additionalHeaders additional headers to include in the presigned URL
	 * @return a PresignedUrl containing the URL and expiration information
	 * @since 0.3.0
	 */
	public PresignedUrl generatePresignedUrl(Duration expiration, Map<String, String> additionalHeaders) {
		Instant expirationTime = clock.instant().plus(expiration);
		AmzDate amzDate = new AmzDate(clock.instant());
		String credentialScope = "%s/%s/s3/aws4_request".formatted(amzDate.yymmdd(), this.region);
		String credential = "%s/%s".formatted(this.accessKeyId, credentialScope);

		TreeMap<String, String> queryParams = new TreeMap<>();
		queryParams.put("X-Amz-Algorithm", AWS4_HMAC_SHA256);
		queryParams.put("X-Amz-Credential", credential);
		queryParams.put("X-Amz-Date", amzDate.date());
		queryParams.put("X-Amz-Expires", String.valueOf(expiration.getSeconds()));
		queryParams.put("X-Amz-SignedHeaders", "host");

		if (!canonicalQueryString.isEmpty()) {
			String[] pairs = canonicalQueryString.split("&");
			for (String pair : pairs) {
				String[] parts = pair.split("=", 2);
				if (parts.length == 2) {
					queryParams.put(parts[0], parts[1]);
				}
			}
		}

		TreeMap<String, String> headers = new TreeMap<>();
		StringBuilder host = new StringBuilder(this.endpoint.getHost());
		if (this.endpoint.getPort() != -1) {
			host.append(":").append(this.endpoint.getPort());
		}
		headers.put(HttpHeaders.HOST, host.toString());

		if (additionalHeaders != null) {
			headers.putAll(additionalHeaders);
			String signedHeaders = headers.keySet().stream().map(String::toLowerCase).collect(Collectors.joining(";"));
			queryParams.put("X-Amz-SignedHeaders", signedHeaders);
		}

		String canonicalQueryStringForSigning = queryParams.entrySet()
			.stream()
			.map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
			.collect(Collectors.joining("&"));

		String canonicalHeaders = headers.entrySet()
			.stream()
			.map(e -> "%s:%s".formatted(e.getKey().toLowerCase(), e.getValue()))
			.collect(Collectors.joining("\n")) + "\n";
		String signedHeaders = headers.keySet().stream().map(String::toLowerCase).collect(Collectors.joining(";"));

		String canonicalRequest = String.join("\n", method.name(), canonicalUri, canonicalQueryStringForSigning,
				canonicalHeaders, signedHeaders, UNSIGNED_PAYLOAD);

		String hashedCanonicalRequest = S3RequestSigningUtils
			.encodeHex(S3RequestSigningUtils.sha256Hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
		String stringToSign = String.join("\n", AWS4_HMAC_SHA256, amzDate.date(), credentialScope,
				hashedCanonicalRequest);
		String signature = this.sign(stringToSign, amzDate);

		queryParams.put("X-Amz-Signature", signature);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUri(this.endpoint).path(canonicalUri);

		queryParams.forEach(builder::queryParam);

		URI presignedUri = builder.build().toUri();

		return new PresignedUrl(presignedUri, expirationTime, additionalHeaders != null ? additionalHeaders : Map.of());
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@Override
	public String toString() {
		return "S3Request{" + "endpoint=" + endpoint + ", region='" + region + '\'' + ", accessKeyId='" + accessKeyId
				+ '\'' + ", method=" + method + ", canonicalUri='" + canonicalUri + '\'' + ", canonicalQueryString='"
				+ canonicalQueryString + '\'' + '}';
	}

}
