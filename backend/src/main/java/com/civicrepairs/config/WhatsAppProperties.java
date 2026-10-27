package com.civicrepairs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp Cloud API. Without an access token the outbound adapter only logs; without an app secret the webhook
 * rejects every delivery (signatures cannot be checked).
 */
@ConfigurationProperties("civic.whatsapp")
public record WhatsAppProperties(
		String phoneNumberId,
		String accessToken,
		String appSecret,
		String verifyToken,
		String graphBaseUrl) {

	public boolean canSend() {
		return accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
	}
}
