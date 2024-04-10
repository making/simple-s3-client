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
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jilt.Builder;
import org.jilt.BuilderStyle;
import org.jilt.Opt;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.util.UriComponentsBuilder;

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

	public static final String AWS4_HMAC_SHA256 = "AWS4-HMAC-SHA256";

	private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

	private URI uri;

	private HttpHeaders httpHeaders;

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
		String contentSha256 = content == null ? UNSIGNED_PAYLOAD : encodeHex(sha256Hash(content.body()));
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

	public URI uri() {
		return this.uri;
	}

	public Consumer<HttpHeaders> headers() {
		return headers -> headers.addAll(this.httpHeaders);
	}

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

	@Override
	public String toString() {
		return "S3Request{" + "endpoint=" + endpoint + ", region='" + region + '\'' + ", accessKeyId='" + accessKeyId
				+ '\'' + ", method=" + method + ", canonicalUri='" + canonicalUri + '\'' + ", canonicalQueryString='"
				+ canonicalQueryString + '\'' + '}';
	}

}
