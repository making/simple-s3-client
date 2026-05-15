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

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static am.ik.s3.S3RequestBuilder.s3Request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Integration tests for S3 versioning features using LocalStack.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersioningIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(VersioningIntegrationTest.class);

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.0.0"))
		.withServices(S3);

	private S3Client client;

	private RestClient restClient;

	private RestTemplate restTemplate;

	private String bucket;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().messageConverters(converters -> {
			converters.add(new MappingJackson2XmlHttpMessageConverter());
			converters.add(new ResourceHttpMessageConverter());
		}).build();

		restTemplate = new RestTemplate();
		restTemplate.getMessageConverters().add(new MappingJackson2XmlHttpMessageConverter());

		client = S3Client.builder()
			.endpoint(localstack.getEndpointOverride(S3).toString())
			.region(localstack.getRegion())
			.credentials(localstack.getAccessKey(), localstack.getSecretKey())
			.restClient(restClient)
			.build();

		bucket = "test-bucket-" + UUID.randomUUID();

		// Create test bucket
		client.bucket(bucket).create();
		// Enable versioning on the bucket
		client.bucket(bucket).enableVersioning();
	}

	@AfterEach
	void tearDown() {
		try {
			// Delete all versions and delete markers
			ListVersionsResult versions = client.bucket(bucket).listVersions();
			if (versions.versions() != null) {
				versions.versions().forEach(version -> {
					if (version.versionId() != null && !version.versionId().equals("null")) {
						client.bucket(bucket).object(version.key()).version(version.versionId()).delete();
					}
				});
			}
			if (versions.deleteMarkers() != null) {
				versions.deleteMarkers().forEach(marker -> {
					if (marker.versionId() != null && !marker.versionId().equals("null")) {
						client.bucket(bucket).object(marker.key()).version(marker.versionId()).delete();
					}
				});
			}
			// Delete bucket
			client.bucket(bucket).delete();
		}
		catch (Exception e) {
			logger.warn("Failed to clean up test bucket", e);
		}
	}

	@Test
	void testListVersions() {
		// Upload multiple versions of the same object
		String key = "versioned-object.txt";
		client.bucket(bucket).object(key).put("Version 1", MediaType.TEXT_PLAIN);
		client.bucket(bucket).object(key).put("Version 2", MediaType.TEXT_PLAIN);
		client.bucket(bucket).object(key).put("Version 3", MediaType.TEXT_PLAIN);

		// List all versions
		ListVersionsResult result = client.bucket(bucket).listVersions();
		assertThat(result).isNotNull();
		assertThat(result.name()).isEqualTo(bucket);
		assertThat(result.versions()).hasSize(3);

		// Verify versions are ordered from newest to oldest
		List<Version> versions = result.versions();
		assertThat(versions.get(0).key()).isEqualTo(key);
		assertThat(versions.get(0).isLatest()).isTrue();
		assertThat(versions.get(1).isLatest()).isFalse();
		assertThat(versions.get(2).isLatest()).isFalse();

		// Verify all versions have unique version IDs
		List<String> versionIds = versions.stream().map(Version::versionId).collect(Collectors.toList());
		assertThat(versionIds).doesNotHaveDuplicates();
		assertThat(versionIds).allMatch(StringUtils::hasText);

		logger.info("Version IDs: {}", versionIds);
	}

	@Test
	void testListVersionsForSpecificObject() {
		// Upload multiple objects
		client.bucket(bucket).object("object1.txt").put("Content 1");
		client.bucket(bucket).object("object2.txt").put("Content 2");
		client.bucket(bucket).object("object1.txt").put("Content 1 Updated");

		// List versions for a specific object
		ListVersionsResult result = client.bucket(bucket).object("object1.txt").listVersions();
		assertThat(result.versions()).hasSize(2);
		assertThat(result.versions()).allMatch(v -> v.key().equals("object1.txt"));

		// Verify version IDs are valid
		assertThat(result.versions()).allMatch(v -> StringUtils.hasText(v.versionId()));
	}

	@Test
	void testGetSpecificVersion() {
		String key = "multi-version.txt";
		// Upload multiple versions
		client.bucket(bucket).object(key).put("Version 1", MediaType.TEXT_PLAIN);
		client.bucket(bucket).object(key).put("Version 2", MediaType.TEXT_PLAIN);
		client.bucket(bucket).object(key).put("Version 3", MediaType.TEXT_PLAIN);

		// Get version IDs
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		List<Version> versionList = versions.versions();
		String latestVersionId = versionList.get(0).versionId();
		String middleVersionId = versionList.get(1).versionId();
		String oldestVersionId = versionList.get(2).versionId();

		// Get specific versions
		String latestContent = client.bucket(bucket).object(key).version(latestVersionId).getAsString();
		String middleContent = client.bucket(bucket).object(key).version(middleVersionId).getAsString();
		String oldestContent = client.bucket(bucket).object(key).version(oldestVersionId).getAsString();

		assertThat(latestContent).isEqualTo("Version 3");
		assertThat(middleContent).isEqualTo("Version 2");
		assertThat(oldestContent).isEqualTo("Version 1");

		// Verify that getting without version ID returns the latest
		String currentContent = client.bucket(bucket).object(key).getAsString();
		assertThat(currentContent).isEqualTo("Version 3");
	}

	@Test
	void testDeleteSpecificVersion() {
		String key = "delete-version-test.txt";
		// Upload multiple versions
		client.bucket(bucket).object(key).put("Version 1");
		client.bucket(bucket).object(key).put("Version 2");
		client.bucket(bucket).object(key).put("Version 3");

		// Get version IDs
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		String middleVersionId = versions.versions().get(1).versionId();

		// Delete the middle version
		client.bucket(bucket).object(key).version(middleVersionId).delete();

		// Verify the version is deleted
		ListVersionsResult afterDelete = client.bucket(bucket).listVersions();
		assertThat(afterDelete.versions()).hasSize(2);
		assertThat(afterDelete.versions()).noneMatch(v -> v.versionId().equals(middleVersionId));

		// Verify other versions are still accessible
		String latestContent = client.bucket(bucket).object(key).getAsString();
		assertThat(latestContent).isEqualTo("Version 3");
	}

	@Test
	void testDeleteMarkers() {
		String key = "delete-marker-test.txt";
		// Upload an object
		client.bucket(bucket).object(key).put("Original content");

		// Delete the object (creates a delete marker)
		client.bucket(bucket).object(key).delete();

		// Try to get the object - should fail
		assertThatThrownBy(() -> client.bucket(bucket).object(key).getAsString()).isInstanceOf(Exception.class);

		// List versions should show both the object and the delete marker
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		assertThat(versions.versions()).hasSize(1);
		assertThat(versions.deleteMarkers()).hasSize(1);
		assertThat(versions.deleteMarkers().get(0).key()).isEqualTo(key);
		assertThat(versions.deleteMarkers().get(0).isLatest()).isTrue();

		// Delete the delete marker to restore the object
		String deleteMarkerId = versions.deleteMarkers().get(0).versionId();
		client.bucket(bucket).object(key).version(deleteMarkerId).delete();

		// Now the object should be accessible again
		String content = client.bucket(bucket).object(key).getAsString();
		assertThat(content).isEqualTo("Original content");
	}

	@Test
	void testVersionedCopy() {
		String sourceKey = "source.txt";
		String destKey = "destination.txt";

		// Upload multiple versions
		client.bucket(bucket).object(sourceKey).put("Version 1");
		client.bucket(bucket).object(sourceKey).put("Version 2");

		// Get the older version ID
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		String olderVersionId = versions.versions().get(1).versionId();

		// Copy the older version to a new object
		client.bucket(bucket).object(sourceKey).version(olderVersionId).copyTo(bucket, destKey);

		// Verify the copied content
		String copiedContent = client.bucket(bucket).object(destKey).getAsString();
		assertThat(copiedContent).isEqualTo("Version 1");
	}

	@Test
	void testPresignedUrlWithVersion() {
		String key = "presigned-version.txt";
		// Upload multiple versions
		client.bucket(bucket).object(key).put("Version 1");
		client.bucket(bucket).object(key).put("Version 2");

		// Get version IDs
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		String olderVersionId = versions.versions().get(1).versionId();

		// Generate presigned URL for the older version
		PresignedUrl presignedUrl = client.bucket(bucket)
			.object(key)
			.version(olderVersionId)
			.presignedUrl(HttpMethod.GET, Duration.ofMinutes(5));

		// Use the presigned URL to get the content
		String content = restClient.get().uri(presignedUrl.url()).retrieve().body(String.class);
		assertThat(content).isEqualTo("Version 1");
	}

	@Test
	void testVersioningWithLowLevelApi() {
		String key = "low-level-version.txt";

		// Upload multiple versions using low-level API
		S3Request putRequest1 = s3Request().endpoint(localstack.getEndpointOverride(S3))
			.region(localstack.getRegion())
			.accessKeyId(localstack.getAccessKey())
			.secretAccessKey(localstack.getSecretKey())
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket).key(key))
			.content(S3Content.of("Low-level version 1", MediaType.TEXT_PLAIN))
			.build();
		restTemplate.exchange(putRequest1.toEntityBuilder().body("Low-level version 1"), Void.class);

		S3Request putRequest2 = s3Request().endpoint(localstack.getEndpointOverride(S3))
			.region(localstack.getRegion())
			.accessKeyId(localstack.getAccessKey())
			.secretAccessKey(localstack.getSecretKey())
			.method(HttpMethod.PUT)
			.path(b -> b.bucket(bucket).key(key))
			.content(S3Content.of("Low-level version 2", MediaType.TEXT_PLAIN))
			.build();
		restTemplate.exchange(putRequest2.toEntityBuilder().body("Low-level version 2"), Void.class);

		// List versions using low-level API
		S3Request listVersionsRequest = s3Request().endpoint(localstack.getEndpointOverride(S3))
			.region(localstack.getRegion())
			.accessKeyId(localstack.getAccessKey())
			.secretAccessKey(localstack.getSecretKey())
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket))
			.build()
			.listVersions();

		ListVersionsResult versionsResult = restTemplate
			.exchange(listVersionsRequest.toEntityBuilder().build(), ListVersionsResult.class)
			.getBody();

		assertThat(versionsResult).isNotNull();
		assertThat(versionsResult.versions()).hasSize(2);

		// Get specific version using low-level API
		String versionId = versionsResult.versions().get(1).versionId();
		S3Request getVersionRequest = s3Request().endpoint(localstack.getEndpointOverride(S3))
			.region(localstack.getRegion())
			.accessKeyId(localstack.getAccessKey())
			.secretAccessKey(localstack.getSecretKey())
			.method(HttpMethod.GET)
			.path(b -> b.bucket(bucket).key(key))
			.build()
			.withVersionId(versionId);

		String versionContent = restTemplate.exchange(getVersionRequest.toEntityBuilder().build(), String.class)
			.getBody();
		assertThat(versionContent).isEqualTo("Low-level version 1");
	}

	@Test
	void testVersioningWithBinaryContent() {
		String key = "binary-version.dat";
		byte[] data1 = new byte[] { 1, 2, 3, 4, 5 };
		byte[] data2 = new byte[] { 6, 7, 8, 9, 10 };

		// Upload binary versions
		client.bucket(bucket).object(key).put(data1, MediaType.APPLICATION_OCTET_STREAM);
		client.bucket(bucket).object(key).put(data2, MediaType.APPLICATION_OCTET_STREAM);

		// Get version IDs
		ListVersionsResult versions = client.bucket(bucket).listVersions();
		String olderVersionId = versions.versions().get(1).versionId();

		// Get specific version as bytes
		byte[] olderData = client.bucket(bucket).object(key).version(olderVersionId).getAsBytes();
		assertThat(olderData).isEqualTo(data1);

		// Get latest version
		byte[] latestData = client.bucket(bucket).object(key).getAsBytes();
		assertThat(latestData).isEqualTo(data2);
	}

	@Test
	void testListVersionsWithPagination() {
		// Upload multiple objects to test pagination
		for (int i = 0; i < 5; i++) {
			String key = String.format("object-%02d.txt", i);
			client.bucket(bucket).object(key).put("Content " + i);
			client.bucket(bucket).object(key).put("Updated content " + i);
		}

		// List versions with max-keys limit
		ListVersionsResult firstPage = client.bucket(bucket).listVersions(null, null, null, 3);
		assertThat(firstPage.versions()).hasSize(3);
		assertThat(firstPage.isTruncated()).isTrue();

		// Get next page using markers
		String nextKeyMarker = firstPage.versions().get(2).key();
		String nextVersionIdMarker = firstPage.versions().get(2).versionId();
		ListVersionsResult secondPage = client.bucket(bucket).listVersions(null, nextKeyMarker, nextVersionIdMarker, 3);
		assertThat(secondPage.versions()).isNotEmpty();
	}

	@Test
	void testVersioningConfiguration() {
		// Create a new bucket for versioning configuration test
		String testBucket = "versioning-config-test-" + UUID.randomUUID();
		client.bucket(testBucket).create();

		try {
			// Initially, versioning should not be enabled
			VersioningConfiguration config = client.bucket(testBucket).getVersioningConfiguration();
			assertThat(config.status()).isNull();

			// Enable versioning
			client.bucket(testBucket).enableVersioning();

			// Check that versioning is enabled
			config = client.bucket(testBucket).getVersioningConfiguration();
			assertThat(config.status()).isEqualTo(VersioningConfiguration.VersioningStatus.ENABLED);

			// Suspend versioning
			client.bucket(testBucket).suspendVersioning();

			// Check that versioning is suspended
			config = client.bucket(testBucket).getVersioningConfiguration();
			assertThat(config.status()).isEqualTo(VersioningConfiguration.VersioningStatus.SUSPENDED);

			// Test setting versioning configuration directly
			client.bucket(testBucket).setVersioningConfiguration(VersioningConfiguration.enabled());
			config = client.bucket(testBucket).getVersioningConfiguration();
			assertThat(config.status()).isEqualTo(VersioningConfiguration.VersioningStatus.ENABLED);
		}
		finally {
			// Clean up
			try {
				client.bucket(testBucket).delete();
			}
			catch (Exception e) {
				logger.warn("Failed to clean up versioning configuration test bucket", e);
			}
		}
	}

}