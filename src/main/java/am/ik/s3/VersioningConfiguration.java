package am.ik.s3;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Represents the versioning configuration for an S3 bucket. This record is used to enable
 * or disable object versioning on a bucket.
 *
 * @param status The versioning status
 * @since 0.3.0
 */
@JacksonXmlRootElement(localName = "VersioningConfiguration")
public record VersioningConfiguration(@JacksonXmlProperty(localName = "Status") VersioningStatus status) {

	public String toXml() {
		return "<VersioningConfiguration><Status>%s</Status></VersioningConfiguration>".formatted(status.getValue());
	}

	/**
	 * Creates a versioning configuration with versioning enabled.
	 * @return A new VersioningConfiguration with status ENABLED
	 */
	public static VersioningConfiguration enabled() {
		return new VersioningConfiguration(VersioningStatus.ENABLED);
	}

	/**
	 * Creates a versioning configuration with versioning suspended.
	 * @return A new VersioningConfiguration with status SUSPENDED
	 */
	public static VersioningConfiguration suspended() {
		return new VersioningConfiguration(VersioningStatus.SUSPENDED);
	}

	/**
	 * Enumeration representing the versioning status of an S3 bucket.
	 *
	 * @since 0.3.0
	 */
	public enum VersioningStatus {

		/**
		 * Versioning is enabled for the bucket.
		 */
		ENABLED("Enabled"),

		/**
		 * Versioning is suspended for the bucket.
		 */
		SUSPENDED("Suspended");

		private final String value;

		VersioningStatus(String value) {
			this.value = value;
		}

		/**
		 * Returns the string representation of the versioning status as used in S3 API.
		 * @return the string value
		 */
		@JsonValue
		public String getValue() {
			return value;
		}

		/**
		 * Returns the VersioningStatus enum from its string representation.
		 * @param value the string value
		 * @return the corresponding VersioningStatus enum
		 * @throws IllegalArgumentException if the value is not recognized
		 */
		public static VersioningStatus fromValue(String value) {
			if (value == null) {
				return null;
			}
			for (VersioningStatus status : values()) {
				if (status.value.equalsIgnoreCase(value)) {
					return status;
				}
			}
			throw new IllegalArgumentException("Unknown versioning status: " + value);
		}

		@Override
		public String toString() {
			return value;
		}

	}

}