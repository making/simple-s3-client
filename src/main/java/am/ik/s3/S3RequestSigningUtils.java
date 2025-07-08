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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for AWS S3 request signing operations.
 *
 * @since 0.3.0
 */
final class S3RequestSigningUtils {

	private S3RequestSigningUtils() {
	}

	/**
	 * Computes HMAC-SHA256 of the given data using the given key.
	 * @param data the data to sign
	 * @param key the key to use for signing
	 * @return the HMAC-SHA256 signature
	 */
	public static byte[] hmacSHA256(String data, byte[] key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
		}
	}

	/**
	 * Computes SHA-256 hash of the given data.
	 * @param data the data to hash
	 * @return the SHA-256 hash
	 */
	public static byte[] sha256Hash(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return md.digest(data);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Failed to compute SHA-256", e);
		}
	}

	/**
	 * Encodes the given byte array as a lowercase hexadecimal string.
	 * @param data the byte array to encode
	 * @return the hexadecimal string representation
	 */
	public static String encodeHex(byte[] data) {
		HexFormat hex = HexFormat.of();
		StringBuilder sb = new StringBuilder();
		for (byte datum : data) {
			sb.append(hex.toHexDigits(datum));
		}
		return sb.toString();
	}

}