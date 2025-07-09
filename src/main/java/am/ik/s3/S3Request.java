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
import java.util.LinkedHashMap;
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

	private final S3Path s3Path;

	private final String canonicalUri;

	private final String canonicalQueryString;

	private final S3Content content;

	private final Clock clock;

	private final Map<String, String> additionalHeaders;

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
	 * @param content the request content (can be ByteArrayS3Content or StreamS3Content)
	 * @param clock the clock to use for timestamps
	 * @param additionalHeaders additional HTTP headers to include in the request
	 */
	@Builder(style = BuilderStyle.STAGED)
	public S3Request(URI endpoint, String region, String accessKeyId, String secretAccessKey, HttpMethod method,
			Function<S3PathBuilder, S3PathBuilder> path, @Opt String canonicalQueryString, @Opt S3Content content,
			@Opt Clock clock, @Opt Map<String, String> additionalHeaders) {
		this.endpoint = endpoint;
		this.region = region;
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.method = method;
		if (path == null) {
			this.s3Path = S3PathBuilder.s3Path().bucket("").key("/").build();
		}
		else {
			this.s3Path = path.apply(S3PathBuilder.s3Path()).build();
		}
		this.canonicalUri = s3Path.toCanonicalUri();
		this.canonicalQueryString = Objects.requireNonNullElse(canonicalQueryString, "");
		this.content = content;
		this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
		this.additionalHeaders = Objects.requireNonNullElse(additionalHeaders, Map.of());
		this.init();
	}

	private void init() {
		AmzDate amzDate = new AmzDate(this.clock.instant());
		String contentSha256;
		// Determine content SHA256 based on content type using pattern matching
		if (content instanceof S3Content.ByteArrayS3Content byteArrayContent) {
			contentSha256 = S3RequestSigningUtils.encodeHex(S3RequestSigningUtils.sha256Hash(byteArrayContent.body()));
		}
		else {
			contentSha256 = UNSIGNED_PAYLOAD;
		}
		TreeMap<String, String> headers = new TreeMap<>();
		headers.put(HttpHeaders.HOST, buildHostHeader());
		headers.put(AmzHttpHeaders.X_AMZ_CONTENT_SHA256, contentSha256);
		headers.put(AmzHttpHeaders.X_AMZ_DATE, amzDate.date());

		// Handle content headers based on content type
		if (content != null) {
			if (content instanceof S3Content.ByteArrayS3Content byteArrayContent) {
				headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(byteArrayContent.body().length));
				if (byteArrayContent.mediaType() != null) {
					headers.put(HttpHeaders.CONTENT_TYPE, byteArrayContent.mediaType().toString());
				}
			}
		}

		// Add additional headers to the signing headers
		headers.putAll(additionalHeaders);
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
		String canonicalHeaders = buildCanonicalHeaders(headers);
		String signedHeaders = buildSignedHeaders(headers);
		String canonicalRequest = String.join("\n", method.name(), canonicalUri, canonicalQueryString, canonicalHeaders,
				signedHeaders, payloadHash);
		// Step 2: Create a hash of the canonical request
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-canonical-request-hash
		String hashedCanonicalRequest = S3RequestSigningUtils
			.encodeHex(S3RequestSigningUtils.sha256Hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
		// Step 3: Create a string to sign
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-string-to-sign
		String credentialScope = getCredentialScope(amzDate);
		String stringToSign = String.join("\n", AWS4_HMAC_SHA256, amzDate.date(), credentialScope,
				hashedCanonicalRequest);
		// Step 4: Calculate the signature
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#calculate-signature
		String signature = S3RequestSigningUtils.generateSignature(this.secretAccessKey, this.region, stringToSign,
				amzDate);
		// Step 5: Add the signature to the request
		// https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#add-signature-to-request
		String credential = getCredential(credentialScope);
		return "%s Credential=%s,SignedHeaders=%s,Signature=%s".formatted(AWS4_HMAC_SHA256, credential, signedHeaders,
				signature);
	}

	/**
	 * Generates a presigned URL for this S3 request.
	 * @param expiration the duration until the URL expires
	 * @return a PresignedUrl containing the URL and expiration information
	 * @since 0.3.0
	 */
	public PresignedUrl presignedUrl(Duration expiration) {
		return presignedUrl(expiration, null);
	}

	/**
	 * Generates a presigned URL for this S3 request with additional headers.
	 * @param expiration the duration until the URL expires
	 * @param additionalHeaders additional headers to include in the presigned URL
	 * @return a PresignedUrl containing the URL and expiration information
	 * @since 0.3.0
	 */
	public PresignedUrl presignedUrl(Duration expiration, Map<String, String> additionalHeaders) {
		Instant expirationTime = clock.instant().plus(expiration);
		AmzDate amzDate = new AmzDate(clock.instant());
		String credentialScope = getCredentialScope(amzDate);
		String credential = getCredential(credentialScope);

		TreeMap<String, String> queryParams = new TreeMap<>();
		queryParams.put(AmzHttpHeaders.X_AMZ_ALGORITHM, AWS4_HMAC_SHA256);
		queryParams.put(AmzHttpHeaders.X_AMZ_CREDENTIAL, credential);
		queryParams.put(AmzHttpHeaders.X_AMZ_DATE, amzDate.date());
		queryParams.put(AmzHttpHeaders.X_AMZ_EXPIRES, String.valueOf(expiration.getSeconds()));
		queryParams.put(AmzHttpHeaders.X_AMZ_SIGNED_HEADERS, "host");

		if (!this.canonicalQueryString.isEmpty()) {
			String[] pairs = this.canonicalQueryString.split("&");
			for (String pair : pairs) {
				String[] parts = pair.split("=", 2);
				if (parts.length == 2) {
					queryParams.put(parts[0], parts[1]);
				}
			}
		}

		TreeMap<String, String> headers = new TreeMap<>();
		headers.put(HttpHeaders.HOST, buildHostHeader());

		if (additionalHeaders != null) {
			headers.putAll(additionalHeaders);
			String signedHeaders = buildSignedHeaders(headers);
			queryParams.put(AmzHttpHeaders.X_AMZ_SIGNED_HEADERS, signedHeaders);
		}

		String canonicalQueryStringForSigning = queryParams.entrySet()
			.stream()
			.map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
			.collect(Collectors.joining("&"));

		String canonicalHeaders = buildCanonicalHeaders(headers);
		String signedHeaders = buildSignedHeaders(headers);

		String canonicalRequest = String.join("\n", method.name(), canonicalUri, canonicalQueryStringForSigning,
				canonicalHeaders, signedHeaders, UNSIGNED_PAYLOAD);

		String hashedCanonicalRequest = S3RequestSigningUtils
			.encodeHex(S3RequestSigningUtils.sha256Hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
		String stringToSign = String.join("\n", AWS4_HMAC_SHA256, amzDate.date(), credentialScope,
				hashedCanonicalRequest);
		String signature = S3RequestSigningUtils.generateSignature(this.secretAccessKey, this.region, stringToSign,
				amzDate);

		queryParams.put(AmzHttpHeaders.X_AMZ_SIGNATURE, signature);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUri(this.endpoint).path(canonicalUri);

		queryParams.forEach(builder::queryParam);

		URI presignedUri = builder.build().toUri();

		return new PresignedUrl(presignedUri, expirationTime, additionalHeaders != null ? additionalHeaders : Map.of());
	}

	/**
	 * Generates a presigned POST form for this S3 request using the S3 path information.
	 * @param expiration the duration until the form expires
	 * @return a PresignedPostForm.Generator for further configuration
	 * @since 0.3.0
	 */
	public PresignedPostForm.Generator presignedPostForm(Duration expiration) {
		Instant expirationTime = this.clock.instant().plus(expiration);
		AmzDate amzDate = new AmzDate(this.clock.instant());

		String bucketName = this.s3Path.bucket();
		String objectKey = this.s3Path.key();
		URI url = UriComponentsBuilder.fromUri(endpoint).path("/" + bucketName).build().toUri();

		String credentialScope = getCredentialScope(amzDate);
		String credential = getCredential(credentialScope);

		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("key", objectKey);
		fields.put("bucket", bucketName);
		fields.put(AmzHttpHeaders.X_AMZ_ALGORITHM, AWS4_HMAC_SHA256);
		fields.put(AmzHttpHeaders.X_AMZ_CREDENTIAL, credential);
		fields.put(AmzHttpHeaders.X_AMZ_DATE, amzDate.date());

		return new PresignedPostForm.Generator(url, expirationTime, stringToSign -> S3RequestSigningUtils
			.generateSignature(this.secretAccessKey, this.region, stringToSign, amzDate)).addFields(fields);
	}

	/**
	 * Creates a new S3Request for initiating multipart upload.
	 * @return a new S3Request configured for InitiateMultipartUpload operation
	 * @since 0.3.0
	 */
	public S3Request initiateMultipartUpload() {
		return prepareRequestBuilder().method(HttpMethod.POST)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString("uploads=")
			.build();
	}

	/**
	 * Creates a new S3Request for uploading a part in a multipart upload.
	 * @param uploadId the upload ID from initiate multipart upload
	 * @param partNumber the part number (must be between 1 and 10,000)
	 * @param partData the data for this part
	 * @return a new S3Request configured for UploadPart operation
	 * @since 0.3.0
	 */
	public S3Request uploadPart(String uploadId, int partNumber, byte[] partData) {
		String queryString = "partNumber=" + partNumber + "&uploadId=" + urlEncode(uploadId);
		return prepareRequestBuilder().method(HttpMethod.PUT)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(queryString)
			.content(S3Content.of(partData, org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
			.build();
	}

	/**
	 * Creates a new S3Request for completing a multipart upload.
	 * @param uploadId the upload ID from initiate multipart upload
	 * @param completeRequest the complete multipart upload request body
	 * @return a new S3Request configured for CompleteMultipartUpload operation
	 * @since 0.3.0
	 */
	public S3Request completeMultipartUpload(String uploadId, CompleteMultipartUpload completeRequest) {
		String queryString = "uploadId=" + urlEncode(uploadId);
		try {
			com.fasterxml.jackson.dataformat.xml.XmlMapper xmlMapper = new com.fasterxml.jackson.dataformat.xml.XmlMapper();
			String xmlBody = xmlMapper.writeValueAsString(completeRequest);
			// Use text/xml content type which is more compatible with S3/MinIO
			org.springframework.http.MediaType xmlMediaType = org.springframework.http.MediaType
				.parseMediaType("text/xml; charset=utf-8");
			return prepareRequestBuilder().method(HttpMethod.POST)
				.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
				.canonicalQueryString(queryString)
				.content(S3Content.of(xmlBody, xmlMediaType))
				.build();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize CompleteMultipartUpload request", e);
		}
	}

	/**
	 * Creates a new S3Request for aborting a multipart upload.
	 * @param uploadId the upload ID from initiate multipart upload
	 * @return a new S3Request configured for AbortMultipartUpload operation
	 * @since 0.3.0
	 */
	public S3Request abortMultipartUpload(String uploadId) {
		String queryString = "uploadId=" + urlEncode(uploadId);
		return prepareRequestBuilder().method(HttpMethod.DELETE)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(queryString)
			.build();
	}

	/**
	 * Creates a new S3Request for listing parts of a multipart upload.
	 * @param uploadId the upload ID from initiate multipart upload
	 * @return a new S3Request configured for ListParts operation
	 * @since 0.3.0
	 */
	public S3Request listParts(String uploadId) {
		String queryString = "uploadId=" + urlEncode(uploadId);
		return prepareRequestBuilder().method(HttpMethod.GET)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(queryString)
			.build();
	}

	/**
	 * Creates a new S3Request for streaming object download with Range header support.
	 * @param rangeStart the starting byte position (inclusive)
	 * @param rangeEnd the ending byte position (inclusive)
	 * @return a new S3Request configured for streaming GET operation with Range header
	 * @since 0.3.0
	 */
	public S3Request withRange(long rangeStart, long rangeEnd) {
		if (rangeStart < 0 || rangeEnd < 0 || rangeStart > rangeEnd) {
			throw new IllegalArgumentException("Invalid range: start=%d, end=%d".formatted(rangeStart, rangeEnd));
		}

		String rangeHeader = "bytes=%d-%d".formatted(rangeStart, rangeEnd);
		Map<String, String> rangeHeaders = new TreeMap<>(this.additionalHeaders);
		rangeHeaders.put("Range", rangeHeader);

		return prepareRequestBuilder().method(HttpMethod.GET)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(this.canonicalQueryString)
			.content(this.content)
			.additionalHeaders(rangeHeaders)
			.build();
	}

	/**
	 * Creates a new S3Request for streaming object download with Range header support
	 * (from start to end of file).
	 * @param rangeStart the starting byte position (inclusive)
	 * @return a new S3Request configured for streaming GET operation with Range header
	 * @since 0.3.0
	 */
	public S3Request withRangeFrom(long rangeStart) {
		if (rangeStart < 0) {
			throw new IllegalArgumentException("Invalid range start: " + rangeStart);
		}

		String rangeHeader = "bytes=%d-".formatted(rangeStart);
		Map<String, String> rangeHeaders = new TreeMap<>(this.additionalHeaders);
		rangeHeaders.put("Range", rangeHeader);

		return prepareRequestBuilder().method(HttpMethod.GET)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(this.canonicalQueryString)
			.content(this.content)
			.additionalHeaders(rangeHeaders)
			.build();
	}

	/**
	 * Creates a new S3Request for listing object versions in a bucket.
	 * @return a new S3Request configured for listing object versions
	 * @since 0.3.0
	 */
	public S3Request listVersions() {
		return prepareRequestBuilder().method(HttpMethod.GET)
			.path(b -> b.bucket(this.s3Path.bucket()))
			.canonicalQueryString("versions=")
			.build();
	}

	/**
	 * Creates a new S3Request for listing object versions with parameters.
	 * @param prefix the prefix to filter objects
	 * @param keyMarker the key marker for pagination
	 * @param versionIdMarker the version ID marker for pagination
	 * @param maxKeys the maximum number of keys to return
	 * @return a new S3Request configured for listing object versions
	 * @since 0.3.0
	 */
	public S3Request listVersions(String prefix, String keyMarker, String versionIdMarker, Integer maxKeys) {
		Map<String, String> params = new TreeMap<>();
		params.put("versions", "");
		if (prefix != null && !prefix.isEmpty()) {
			params.put("prefix", urlEncode(prefix));
		}
		if (keyMarker != null && !keyMarker.isEmpty()) {
			params.put("key-marker", urlEncode(keyMarker));
		}
		if (versionIdMarker != null && !versionIdMarker.isEmpty()) {
			params.put("version-id-marker", urlEncode(versionIdMarker));
		}
		if (maxKeys != null) {
			params.put("max-keys", String.valueOf(maxKeys));
		}

		String queryString = buildCanonicalQueryString(params);

		return prepareRequestBuilder().method(HttpMethod.GET)
			.path(b -> b.bucket(this.s3Path.bucket()))
			.canonicalQueryString(queryString)
			.build();
	}

	/**
	 * Creates a new S3Request for getting a specific version of an object.
	 * @param versionId the version ID of the object
	 * @return a new S3Request configured for getting a specific version
	 * @since 0.3.0
	 */
	public S3Request withVersionId(String versionId) {
		if (versionId == null || versionId.isEmpty()) {
			throw new IllegalArgumentException("Version ID cannot be null or empty");
		}

		String queryString = this.canonicalQueryString.isEmpty() ? "versionId=" + urlEncode(versionId)
				: this.canonicalQueryString + "&versionId=" + urlEncode(versionId);

		return prepareRequestBuilder().method(this.method)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString(queryString)
			.content(this.content)
			.additionalHeaders(this.additionalHeaders)
			.build();
	}

	/**
	 * Creates a new S3Request for deleting a specific version of an object.
	 * @param versionId the version ID of the object to delete
	 * @return a new S3Request configured for deleting a specific version
	 * @since 0.3.0
	 */
	public S3Request deleteVersion(String versionId) {
		if (versionId == null || versionId.isEmpty()) {
			throw new IllegalArgumentException("Version ID cannot be null or empty");
		}

		return prepareRequestBuilder().method(HttpMethod.DELETE)
			.path(b -> b.bucket(this.s3Path.bucket()).key(this.s3Path.key()))
			.canonicalQueryString("versionId=" + urlEncode(versionId))
			.build();
	}

	private String getCredentialScope(AmzDate amzDate) {
		return "%s/%s/s3/aws4_request".formatted(amzDate.yymmdd(), this.region);
	}

	/**
	 * Prepares a new S3RequestBuilder with the common base configuration.
	 * @return a configured S3RequestBuilder with endpoint, region, and credentials
	 */
	private S3RequestBuilders.Method prepareRequestBuilder() {
		return S3RequestBuilder.s3Request()
			.endpoint(this.endpoint)
			.region(this.region)
			.accessKeyId(this.accessKeyId)
			.secretAccessKey(this.secretAccessKey);
	}

	/**
	 * Builds the host header value including port if necessary.
	 * @return the host header value
	 */
	private String buildHostHeader() {
		StringBuilder host = new StringBuilder(this.endpoint.getHost());
		if (this.endpoint.getPort() != -1) {
			host.append(":").append(this.endpoint.getPort());
		}
		return host.toString();
	}

	/**
	 * Builds canonical headers string from headers map.
	 * @param headers the headers map
	 * @return the canonical headers string
	 */
	private String buildCanonicalHeaders(TreeMap<String, String> headers) {
		return headers.entrySet()
			.stream()
			.map(e -> "%s:%s".formatted(e.getKey().toLowerCase(), e.getValue()))
			.collect(Collectors.joining("\n")) + "\n";
	}

	/**
	 * Builds signed headers string from headers map.
	 * @param headers the headers map
	 * @return the signed headers string
	 */
	private String buildSignedHeaders(TreeMap<String, String> headers) {
		return headers.keySet().stream().map(String::toLowerCase).collect(Collectors.joining(";"));
	}

	/**
	 * Builds canonical query string from parameters map.
	 * @param params the parameters map
	 * @return the canonical query string
	 */
	private String buildCanonicalQueryString(Map<String, String> params) {
		return params.entrySet()
			.stream()
			.map(e -> e.getValue().isEmpty() ? e.getKey() + "=" : e.getKey() + "=" + e.getValue())
			.collect(Collectors.joining("&"));
	}

	private String getCredential(String credentialScope) {
		return "%s/%s".formatted(this.accessKeyId, credentialScope);
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
