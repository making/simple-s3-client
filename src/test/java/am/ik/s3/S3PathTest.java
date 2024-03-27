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

import org.junit.jupiter.api.Test;

import static am.ik.s3.S3PathBuilder.s3Path;
import static org.assertj.core.api.Assertions.assertThat;

class S3PathTest {

	@Test
	void toCanonicalUri1() {
		S3Path s3Path = s3Path().bucket("receipts")
			.key("a8b1fd9e-ee12-4034-af53-1990b382fd90-4daca818-e207-45dc-93c9-f16e581ceca7-f72b50a3-eaba-44df-ac0d-6f64c96adc15-b1367634-1096-4887-b77d-995c8729975e-b1367634-1096-4887-b77d-995c8729975e-10_5d6b84ce-1afa-4d20-8c35-04af150be933 (1)_v0_v1_v1 (2)_v0_v0.jpeg")
			.build();
		assertThat(s3Path.toCanonicalUri()).isEqualTo(
				"/receipts/a8b1fd9e-ee12-4034-af53-1990b382fd90-4daca818-e207-45dc-93c9-f16e581ceca7-f72b50a3-eaba-44df-ac0d-6f64c96adc15-b1367634-1096-4887-b77d-995c8729975e-b1367634-1096-4887-b77d-995c8729975e-10_5d6b84ce-1afa-4d20-8c35-04af150be933%20%281%29_v0_v1_v1%20%282%29_v0_v0.jpeg");
	}

	@Test
	void toCanonicalUri2() {
		S3Path s3Path = s3Path().bucket("foo").key("%a").build();
		assertThat(s3Path.toCanonicalUri()).isEqualTo("/foo/%25a");
	}

}