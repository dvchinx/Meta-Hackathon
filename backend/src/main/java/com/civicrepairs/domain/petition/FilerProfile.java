package com.civicrepairs.domain.petition;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What an agency needs to identify a petitioner (Ley 1755 de 2015, art. 16). Asked only when the citizen decides to
 * file a petition, stored encrypted and never public. The notification address is the account's email.
 *
 * @param address optional postal address; null when not given
 */
public record FilerProfile(
		IdentificationType idType,
		String idNumber,
		String firstNames,
		String lastNames,
		String mobilePhone,
		String address) {

	public static final int MAX_NAME = 60;
	public static final int MAX_ADDRESS = 120;

	private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]*$");
	private static final Pattern DIGITS = Pattern.compile("^\\d{3,12}$");
	private static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Z0-9]{4,20}$");
	private static final Pattern COLOMBIAN_MOBILE = Pattern.compile("^3\\d{9}$");

	/** Normalizes every field (spaces, separators, country code) and validates it. */
	public FilerProfile {
		if (idType == null) {
			throw new InvalidFilerProfileException("idType");
		}
		idNumber = normalizeIdNumber(idType, idNumber);
		firstNames = normalizeName(firstNames, "firstNames");
		lastNames = normalizeName(lastNames, "lastNames");
		mobilePhone = normalizeMobile(mobilePhone);
		address = normalizeAddress(address);
	}

	public String fullName() {
		return firstNames + " " + lastNames;
	}

	private static String normalizeIdNumber(IdentificationType type, String value) {
		String raw = value == null ? "" : value.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
		if (!(type.isNumeric() ? DIGITS : ALPHANUMERIC).matcher(raw).matches()) {
			throw new InvalidFilerProfileException("idNumber");
		}
		return raw;
	}

	private static String normalizeName(String value, String field) {
		String name = value == null ? "" : value.strip().replaceAll("\\s+", " ");
		if (name.isEmpty() || name.length() > MAX_NAME || !NAME.matcher(name).matches()) {
			throw new InvalidFilerProfileException(field);
		}
		return name;
	}

	private static String normalizeMobile(String value) {
		String digits = value == null ? "" : value.replaceAll("[\\s()\\-]", "");
		if (digits.startsWith("+57")) {
			digits = digits.substring(3);
		}
		if (!COLOMBIAN_MOBILE.matcher(digits).matches()) {
			throw new InvalidFilerProfileException("mobilePhone");
		}
		return digits;
	}

	private static String normalizeAddress(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String address = value.strip().replaceAll("\\s+", " ");
		if (address.length() > MAX_ADDRESS) {
			throw new InvalidFilerProfileException("address");
		}
		return address;
	}
}
