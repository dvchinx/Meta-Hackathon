package com.civicrepairs.adapters.out.whatsapp;

/** Phone numbers never reach the logs in full. */
final class PhoneMask {

	private PhoneMask() {
	}

	static String mask(String phone) {
		if (phone == null || phone.length() < 6) {
			return "***";
		}
		return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 2);
	}
}
