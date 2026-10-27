package com.civicrepairs.application.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppYesNoTest {

	@ParameterizedTest
	@ValueSource(strings = {"Sí", "si", "SI!", "sí, es correcto", "ok", "Dale", "correcto 👍"})
	void understandsYes(String text) {
		assertThat(WhatsAppConversationService.parseYesNo(text)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"No", "no.", "NO, está mal", "cancelar"})
	void understandsNo(String text) {
		assertThat(WhatsAppConversationService.parseYesNo(text)).isFalse();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "tal vez", "sino", "nose", "¿qué?"})
	void anythingElseIsUnclear(String text) {
		assertThat(WhatsAppConversationService.parseYesNo(text)).isNull();
	}
}
