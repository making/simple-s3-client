package am.ik.s3;

import java.net.URI;

/**
 * Configuration holder for S3 client settings. This record holds all the necessary
 * configuration parameters for establishing connections to S3-compatible services.
 *
 * @param endpoint The S3 endpoint URI
 * @param region The AWS region
 * @param accessKeyId The AWS access key ID
 * @param secretAccessKey The AWS secret access key
 * @since 0.3.0
 */
public record S3ClientConfiguration(URI endpoint, String region, String accessKeyId, String secretAccessKey) {

	/**
	 * Validates the configuration parameters.
	 * @throws IllegalArgumentException if any required parameter is null or empty
	 */
	public void validate() {
		if (endpoint == null) {
			throw new IllegalArgumentException("Endpoint must not be null");
		}
		if (region == null || region.isBlank()) {
			throw new IllegalArgumentException("Region must not be null or empty");
		}
		if (accessKeyId == null || accessKeyId.isBlank()) {
			throw new IllegalArgumentException("Access key ID must not be null or empty");
		}
		if (secretAccessKey == null || secretAccessKey.isBlank()) {
			throw new IllegalArgumentException("Secret access key must not be null or empty");
		}
	}
}