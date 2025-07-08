package am.ik.s3;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for S3ClientConfiguration.
 */
class S3ClientConfigurationTest {

	@Test
	void recordShouldHoldConfigurationValues() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "secretKey");

		assertThat(config.endpoint()).isEqualTo(URI.create("https://s3.amazonaws.com"));
		assertThat(config.region()).isEqualTo("us-east-1");
		assertThat(config.accessKeyId()).isEqualTo("accessKey");
		assertThat(config.secretAccessKey()).isEqualTo("secretKey");
	}

	@Test
	void validateShouldPassForValidConfiguration() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "secretKey");

		// Should not throw exception
		config.validate();
	}

	@Test
	void validateShouldThrowExceptionForNullEndpoint() {
		S3ClientConfiguration config = new S3ClientConfiguration(null, "us-east-1", "accessKey", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Endpoint must not be null");
	}

	@Test
	void validateShouldThrowExceptionForNullRegion() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), null,
				"accessKey", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Region must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForEmptyRegion() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "",
				"accessKey", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Region must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForBlankRegion() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "   ",
				"accessKey", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Region must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForNullAccessKeyId() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				null, "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Access key ID must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForEmptyAccessKeyId() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Access key ID must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForBlankAccessKeyId() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"   ", "secretKey");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Access key ID must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForNullSecretAccessKey() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", null);

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Secret access key must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForEmptySecretAccessKey() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Secret access key must not be null or empty");
	}

	@Test
	void validateShouldThrowExceptionForBlankSecretAccessKey() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "   ");

		assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Secret access key must not be null or empty");
	}

	@Test
	void recordShouldSupportEqualsAndHashCode() {
		S3ClientConfiguration config1 = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "secretKey");

		S3ClientConfiguration config2 = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "secretKey");

		S3ClientConfiguration config3 = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "differentSecret");

		assertThat(config1).isEqualTo(config2);
		assertThat(config1).isNotEqualTo(config3);
		assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
	}

	@Test
	void recordShouldSupportToString() {
		S3ClientConfiguration config = new S3ClientConfiguration(URI.create("https://s3.amazonaws.com"), "us-east-1",
				"accessKey", "secretKey");

		String toString = config.toString();

		assertThat(toString).contains("S3ClientConfiguration");
		assertThat(toString).contains("https://s3.amazonaws.com");
		assertThat(toString).contains("us-east-1");
		assertThat(toString).contains("accessKey");
		assertThat(toString).contains("secretKey");
	}

}