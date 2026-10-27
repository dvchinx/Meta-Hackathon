package com.civicrepairs.adapters.in.whatsapp;

import com.civicrepairs.application.port.in.HandleIncomingMessageUseCase.IncomingMessage;
import com.civicrepairs.application.port.in.HandleIncomingMessageUseCase.Kind;
import com.civicrepairs.application.port.out.MessagingPort;
import com.civicrepairs.domain.shared.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Turns a Cloud API webhook payload into incoming messages. Delivery statuses are only logged. */
final class WebhookPayloadParser {

	private static final Logger log = LoggerFactory.getLogger(WebhookPayloadParser.class);

	private WebhookPayloadParser() {
	}

	/** @param phoneNumberId our number; payloads for other numbers of the same app are ignored */
	static List<IncomingMessage> messages(JsonNode payload, String phoneNumberId) {
		List<IncomingMessage> result = new ArrayList<>();
		for (JsonNode entry : payload.path("entry")) {
			for (JsonNode change : entry.path("changes")) {
				JsonNode value = change.path("value");
				String target = value.path("metadata").path("phone_number_id").asString("");
				if (!phoneNumberId.isBlank() && !phoneNumberId.equals(target)) {
					continue;
				}
				for (JsonNode status : value.path("statuses")) {
					logStatus(status);
				}
				for (JsonNode message : value.path("messages")) {
					result.add(toIncoming(message));
				}
			}
		}
		return result;
	}

	private static IncomingMessage toIncoming(JsonNode m) {
		String id = m.path("id").asString();
		String from = m.path("from").asString();
		Instant at = Instant.ofEpochSecond(m.path("timestamp").asLong(Instant.now().getEpochSecond()));
		return switch (m.path("type").asString("")) {
			case "text" -> new IncomingMessage(id, Kind.TEXT, from, m.path("text").path("body").asString(), null, null,
					null, at);
			case "image" -> new IncomingMessage(id, Kind.IMAGE, from, m.path("image").path("caption").asString(null),
					m.path("image").path("id").asString(), null, null, at);
			case "location" -> new IncomingMessage(id, Kind.LOCATION, from, null, null,
					new GeoPoint(m.path("location").path("latitude").asDouble(),
							m.path("location").path("longitude").asDouble()), null, at);
			case "interactive" -> {
				String reply = m.path("interactive").path("button_reply").path("id").asString("");
				Boolean accepted = MessagingPort.CONFIRM_YES.equals(reply) ? Boolean.TRUE
						: MessagingPort.CONFIRM_NO.equals(reply) ? Boolean.FALSE : null;
				yield accepted == null
						? new IncomingMessage(id, Kind.UNSUPPORTED, from, null, null, null, null, at)
						: new IncomingMessage(id, Kind.CONFIRMATION, from, null, null, null, accepted, at);
			}
			default -> new IncomingMessage(id, Kind.UNSUPPORTED, from, null, null, null, null, at);
		};
	}

	/** Never logs the recipient number. Failed deliveries carry Meta's error code (e.g. 131026 undeliverable). */
	private static void logStatus(JsonNode status) {
		String state = status.path("status").asString("");
		if ("failed".equals(state)) {
			JsonNode error = status.path("errors").path(0);
			log.warn("WhatsApp delivery failed for {}: code {} - {} ({})", status.path("id").asString(),
					error.path("code").asInt(), error.path("title").asString(""),
					error.path("error_data").path("details").asString(""));
		} else {
			log.info("WhatsApp message {} {}", status.path("id").asString(), state);
		}
	}
}
