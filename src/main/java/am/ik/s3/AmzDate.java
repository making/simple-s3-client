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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

class AmzDate {

	private static final DateTimeFormatter AMZDATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'");

	private final String date;

	private final String yymmdd;

	public AmzDate(Instant timestamp) {
		final OffsetDateTime dateTime = timestamp.atOffset(ZoneOffset.UTC);
		this.date = AMZDATE_FORMATTER.format(dateTime);
		this.yymmdd = this.date.substring(0, 8);
	}

	public String date() {
		return date;
	}

	public String yymmdd() {
		return yymmdd;
	}

}
