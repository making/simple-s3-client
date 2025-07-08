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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.unit.DataSize;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Represents a presigned POST form for direct browser uploads to S3.
 *
 * @since 0.3.0
 */
public record PresignedPostForm(URI url, Map<String, String> formFields, Instant expiresAt, List<String> conditions) {

	/**
	 * Creates a new builder for presigned POST forms.
	 * @param configuration the S3 client configuration
	 * @param bucketName the bucket name
	 * @param objectKey the object key
	 * @return a new builder instance
	 */
	public static Builder builder(S3ClientConfiguration configuration, String bucketName, String objectKey) {
		return new Builder(configuration, bucketName, objectKey);
	}

	/**
	 * Builder for generating presigned POST forms for direct browser uploads to S3.
	 *
	 * @since 0.3.0
	 */
	public static class Builder {

		private final S3ClientConfiguration configuration;

		private final String bucketName;

		private final String objectKey;

		private Duration expiration = Duration.ofHours(1);

		private final Map<String, String> formFields = new LinkedHashMap<>();

		private final List<String> conditions = new ArrayList<>();

		private Long maxFileSize;

		private Builder(S3ClientConfiguration configuration, String bucketName, String objectKey) {
			this.configuration = configuration;
			this.bucketName = bucketName;
			this.objectKey = objectKey;
		}

		/**
		 * Sets the expiration duration for the presigned POST form.
		 * @param expiration the duration until the form expires
		 * @return this builder
		 */
		public Builder expiration(Duration expiration) {
			this.expiration = expiration;
			return this;
		}

		/**
		 * Sets the maximum file size for uploads.
		 * @param maxFileSize the maximum file size in bytes
		 * @return this builder
		 */
		public Builder maxFileSize(long maxFileSize) {
			this.maxFileSize = maxFileSize;
			return this;
		}

		/**
		 * Sets the maximum file size for uploads.
		 * @param maxFileSize the maximum file size as DataSize
		 * @return this builder
		 */
		public Builder maxFileSize(DataSize maxFileSize) {
			this.maxFileSize = maxFileSize.toBytes();
			return this;
		}

		/**
		 * Adds a form field to be included in the POST form.
		 * @param name the field name
		 * @param value the field value
		 * @return this builder
		 */
		public Builder field(String name, String value) {
			this.formFields.put(name, value);
			return this;
		}

		/**
		 * Adds a condition to the policy.
		 * @param condition the condition string
		 * @return this builder
		 */
		public Builder condition(String condition) {
			this.conditions.add(condition);
			return this;
		}

		/**
		 * Generates the presigned POST form.
		 * @return the presigned POST form
		 */
		public PresignedPostForm generate() {
			Instant expirationTime = Instant.now().plus(expiration);
			AmzDate amzDate = new AmzDate(Instant.now());

			URI url = UriComponentsBuilder.fromUri(configuration.endpoint()).path("/" + bucketName).build().toUri();

			String credentialScope = "%s/%s/s3/aws4_request".formatted(amzDate.yymmdd(), configuration.region());
			String credential = "%s/%s".formatted(configuration.accessKeyId(), credentialScope);

			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("key", objectKey);
			fields.put("bucket", bucketName);
			fields.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
			fields.put("X-Amz-Credential", credential);
			fields.put("X-Amz-Date", amzDate.date());
			fields.putAll(formFields);

			List<String> policyConditions = new ArrayList<>();
			policyConditions.add("{\"bucket\": \"" + bucketName + "\"}");
			policyConditions.add("{\"key\": \"" + objectKey + "\"}");
			policyConditions.add("{\"X-Amz-Algorithm\": \"AWS4-HMAC-SHA256\"}");
			policyConditions.add("{\"X-Amz-Credential\": \"" + credential + "\"}");
			policyConditions.add("{\"X-Amz-Date\": \"" + amzDate.date() + "\"}");

			// Add conditions for form fields
			formFields.forEach((key, value) -> {
				policyConditions.add("{\"" + key + "\": \"" + value + "\"}");
			});

			if (maxFileSize != null) {
				policyConditions.add("[\"content-length-range\", 0, " + maxFileSize + "]");
			}

			policyConditions.addAll(conditions);

			String policy = "{\n" + "  \"expiration\": \"" + expirationTime.toString() + "\",\n"
					+ "  \"conditions\": [\n    " + String.join(",\n    ", policyConditions) + "\n  ]\n" + "}";

			String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
			fields.put("policy", encodedPolicy);

			String signature = generateSignature(encodedPolicy, amzDate);
			fields.put("X-Amz-Signature", signature);

			return new PresignedPostForm(url, fields, expirationTime, conditions);
		}

		private String generateSignature(String policy, AmzDate amzDate) {
			try {
				byte[] kSecret = ("AWS4" + configuration.secretAccessKey()).getBytes(StandardCharsets.UTF_8);
				byte[] kDate = S3RequestSigningUtils.hmacSHA256(amzDate.yymmdd(), kSecret);
				byte[] kRegion = S3RequestSigningUtils.hmacSHA256(configuration.region(), kDate);
				byte[] kService = S3RequestSigningUtils.hmacSHA256("s3", kRegion);
				byte[] kSigning = S3RequestSigningUtils.hmacSHA256("aws4_request", kService);
				byte[] signature = S3RequestSigningUtils.hmacSHA256(policy, kSigning);
				return S3RequestSigningUtils.encodeHex(signature);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to generate signature", e);
			}
		}

	}

}