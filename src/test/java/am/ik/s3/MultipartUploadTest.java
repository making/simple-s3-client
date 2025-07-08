package am.ik.s3;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartUploadTest {

	@Test
	void testMultipartUploadConfiguration() {
		MultipartUploadConfiguration config = MultipartUploadConfiguration.defaultConfiguration();

		assertThat(config.partSize()).isEqualTo(DataSize.ofMegabytes(5));
		assertThat(config.maxConcurrentUploads()).isEqualTo(3);
		assertThat(config.enableProgressTracking()).isTrue();
		assertThat(config.executor()).isNull();
	}

	@Test
	void testMultipartUploadConfigurationBuilder() {
		MultipartUploadConfiguration config = MultipartUploadConfiguration.builder()
			.partSize(DataSize.ofMegabytes(10))
			.maxConcurrentUploads(5)
			.enableProgressTracking(false)
			.build();

		assertThat(config.partSize()).isEqualTo(DataSize.ofMegabytes(10));
		assertThat(config.maxConcurrentUploads()).isEqualTo(5);
		assertThat(config.enableProgressTracking()).isFalse();
		assertThat(config.executor()).isNull();
	}

	@Test
	void testMultipartUploadConfigurationValidation() {
		assertThatThrownBy(() -> {
			MultipartUploadConfiguration.builder()
				.partSize(DataSize.ofMegabytes(1)) // Too small
				.build();
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Part size must be at least " + MultipartUploadConfiguration.MIN_PART_SIZE);

		assertThatThrownBy(() -> {
			MultipartUploadConfiguration.builder()
				.partSize(DataSize.ofGigabytes(10)) // Too large
				.build();
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Part size must not exceed " + MultipartUploadConfiguration.MAX_PART_SIZE);

		assertThatThrownBy(() -> {
			MultipartUploadConfiguration.builder()
				.maxConcurrentUploads(0) // Invalid
				.build();
		}).isInstanceOf(IllegalArgumentException.class).hasMessage("Max concurrent uploads must be positive");
	}

	@Test
	void testProgressCallbackNoOp() {
		ProgressCallback callback = ProgressCallback.noOp();

		// Should not throw any exceptions
		callback.onPartCompleted(1, 1000);
		callback.onUploadCompleted(5000);
		callback.onError(new RuntimeException("Test error"));
	}

	@Test
	void testCompletedPart() {
		CompletedPart part = new CompletedPart("test-etag", 1);

		assertThat(part.etag()).isEqualTo("test-etag");
		assertThat(part.partNumber()).isEqualTo(1);
	}

	@Test
	void testUploadPartResult() {
		UploadPartResult result = new UploadPartResult("test-etag", 1);

		assertThat(result.etag()).isEqualTo("test-etag");
		assertThat(result.partNumber()).isEqualTo(1);
	}

	@Test
	void testMultipartUploadConfigurationWithExecutor() {
		java.util.concurrent.Executor executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		MultipartUploadConfiguration config = MultipartUploadConfiguration.builder()
			.partSize(DataSize.ofMegabytes(8))
			.maxConcurrentUploads(4)
			.enableProgressTracking(true)
			.executor(executor)
			.build();

		assertThat(config.partSize()).isEqualTo(DataSize.ofMegabytes(8));
		assertThat(config.maxConcurrentUploads()).isEqualTo(4);
		assertThat(config.enableProgressTracking()).isTrue();
		assertThat(config.executor()).isEqualTo(executor);
	}

}