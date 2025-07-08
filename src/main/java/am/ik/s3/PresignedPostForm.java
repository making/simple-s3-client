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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.util.unit.DataSize;

/**
 * Represents a presigned POST form for direct browser uploads to S3.
 *
 * @param url the URL to POST the form to
 * @param formFields the form fields that must be included in the POST request
 * @param expiresAt the instant when this presigned POST form expires
 * @param conditions the policy conditions that were used to generate this form
 * @since 0.3.0
 */
public record PresignedPostForm(URI url, Map<String, String> formFields, Instant expiresAt, List<String> conditions) {

	/**
	 * Builder for generating presigned POST forms for direct browser uploads to S3. This
	 * builder can only be created from S3Request and contains pre-computed information.
	 *
	 * @since 0.3.0
	 */
	public static class Generator {

		private final URI url;

		private final Instant expirationTime;

		private final Function<String, String> signatureGenerator;

		private final Map<String, String> formFields = new LinkedHashMap<>();

		private final List<String> conditions = new ArrayList<>();

		private DataSize maxFileSize;

		// Package-private constructor - only S3Request can create this
		Generator(URI url, Instant expirationTime, Function<String, String> signatureGenerator) {
			this.url = Objects.requireNonNull(url, "url must not be null");
			this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime must not be null");
			this.signatureGenerator = Objects.requireNonNull(signatureGenerator, "signatureGenerator must not be null");
		}

		/**
		 * Sets the maximum file size for uploads.
		 * @param maxFileSize the maximum file size in bytes
		 * @return this builder
		 */
		public Generator maxFileSize(long maxFileSize) {
			this.maxFileSize = DataSize.ofBytes(maxFileSize);
			return this;
		}

		/**
		 * Sets the maximum file size for uploads.
		 * @param maxFileSize the maximum file size as DataSize
		 * @return this builder
		 */
		public Generator maxFileSize(DataSize maxFileSize) {
			this.maxFileSize = maxFileSize;
			return this;
		}

		/**
		 * Adds a form field to be included in the POST form.
		 * @param name the field name
		 * @param value the field value
		 * @return this builder
		 */
		public Generator addField(String name, String value) {
			this.formFields.put(name, value);
			return this;
		}

		/**
		 * Adds multiple form fields to be included in the POST form.
		 * @param fields the map of field names to values
		 * @return this builder
		 */
		public Generator addFields(Map<String, String> fields) {
			this.formFields.putAll(fields);
			return this;
		}

		/**
		 * Adds a condition to the policy.
		 * @param condition the condition string
		 * @return this builder
		 */
		public Generator addCondition(String condition) {
			this.conditions.add(condition);
			return this;
		}

		/**
		 * Adds multiple conditions to the policy.
		 * @param conditions the list of condition strings
		 * @return this builder
		 */
		public Generator addConditions(List<String> conditions) {
			this.conditions.addAll(conditions);
			return this;
		}

		/**
		 * Generates the presigned POST form.
		 * @return the presigned POST form
		 */
		public PresignedPostForm generate() {
			List<String> policyConditions = new ArrayList<>();
			Map<String, String> fields = new LinkedHashMap<>(this.formFields);
			// Add conditions for form fields
			fields.forEach((key, value) -> {
				policyConditions.add("""
						{"%s": "%s"}""".formatted(key, value));
			});
			if (maxFileSize != null) {
				policyConditions.add("""
						["content-length-range", 0, %d]""".formatted(maxFileSize.toBytes()));
			}
			policyConditions.addAll(this.conditions);
			String policy = """
					{
					  "expiration": "%s",
					  "conditions": [
					    %s
					  ]
					}""".formatted(expirationTime, String.join(",\n    ", policyConditions));
			String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
			fields.put("policy", encodedPolicy);
			String signature = this.signatureGenerator.apply(encodedPolicy);
			fields.put("X-Amz-Signature", signature);
			return new PresignedPostForm(url, Collections.unmodifiableMap(fields), expirationTime,
					Collections.unmodifiableList(conditions));
		}

	}

}