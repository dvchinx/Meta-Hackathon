package com.civicrepairs.adapters.in.whatsapp;

import com.civicrepairs.application.port.in.HandleIncomingMessageUseCase;
import com.civicrepairs.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** WhatsApp Cloud API webhook: {@code https://civic.jesusflorez.cloud/webhooks/whatsapp}. */
@RestController
@RequestMapping("/webhooks/whatsapp")
class WhatsAppWebhookController {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

	private final HandleIncomingMessageUseCase handleMessage;
	private final MessageDispatcher dispatcher;
	private final WhatsAppProperties properties;
	private final JsonMapper json;

	WhatsAppWebhookController(HandleIncomingMessageUseCase handleMessage, MessageDispatcher dispatcher,
			WhatsAppProperties properties, JsonMapper json) {
		this.handleMessage = handleMessage;
		this.dispatcher = dispatcher;
		this.properties = properties;
		this.json = json;
		if (properties.appSecret() == null || properties.appSecret().isBlank()) {
			log.warn("WHATSAPP_APP_SECRET is not set: every webhook delivery will be rejected.");
		}
	}

	/** Meta's one-time verification when the callback URL is registered. */
	@GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
	ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
			@RequestParam(name = "hub.verify_token", required = false) String token,
			@RequestParam(name = "hub.challenge", required = false) String challenge) {
		String expected = properties.verifyToken();
		boolean ok = "subscribe".equals(mode) && expected != null && !expected.isBlank() && token != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
		return ok ? ResponseEntity.ok(challenge) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	/** Answers 200 right away; messages are handled asynchronously, one at a time per sender. */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> receive(@RequestBody byte[] body,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
		if (!WebhookSignature.isValid(properties.appSecret(), body, signature)) {
			log.warn("Rejected WhatsApp webhook with an invalid signature");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		try {
			var messages = WebhookPayloadParser.messages(json.readTree(body), properties.phoneNumberId());
			for (var message : messages) {
				dispatcher.dispatch(message.from(), () -> handleMessage.handle(message));
			}
		} catch (JacksonException e) {
			log.warn("Ignoring malformed WhatsApp payload: {}", e.getOriginalMessage());
		}
		return ResponseEntity.ok().build();
	}
}
