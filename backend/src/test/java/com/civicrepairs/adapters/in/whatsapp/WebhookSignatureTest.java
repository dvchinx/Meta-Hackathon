package com.civicrepairs.adapters.in.whatsapp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

	private static final byte[] BODY = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	void acceptsTheExactHmacOfTheRawBody() {
		assertThat(WebhookSignature.isValid("secret", BODY, WebhookSignature.sign("secret", BODY))).isTrue();
	}

	@Test
	void rejectsEverythingElse() {
		String good = WebhookSignature.sign("secret", BODY);
		assertThat(WebhookSignature.isValid("other", BODY, good)).isFalse();
		assertThat(WebhookSignature.isValid("secret", "{}".getBytes(StandardCharsets.UTF_8), good)).isFalse();
		assertThat(WebhookSignature.isValid("secret", BODY, null)).isFalse();
		assertThat(WebhookSignature.isValid("secret", BODY, "sha1=abc")).isFalse();
		assertThat(WebhookSignature.isValid("secret", BODY, "sha256=not-hex")).isFalse();
	}

	@Test
	void withoutAnAppSecretNothingIsAccepted() {
		assertThat(WebhookSignature.isValid("", BODY, WebhookSignature.sign("secret", BODY))).isFalse();
		assertThat(WebhookSignature.isValid(null, BODY, "sha256=00")).isFalse();
	}
}
