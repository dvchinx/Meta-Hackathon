package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.shared.GeoPoint;

import java.time.Instant;

/** One chat message from a citizen, already deduplicated and authenticated by the inbound adapter. */
public interface HandleIncomingMessageUseCase {

	void handle(IncomingMessage message);

	enum Kind {
		TEXT,
		IMAGE,
		LOCATION,
		/** Reply to the Sí/No buttons. */
		CONFIRMATION,
		/** Audio, video, documents, stickers... not handled in the MVP. */
		UNSUPPORTED
	}

	/**
	 * @param messageId    channel message id; redeliveries of the same id are ignored
	 * @param from         raw phone number (WhatsApp id); hashed before anything is stored
	 * @param text         text body or image caption
	 * @param mediaId      channel media id for IMAGE
	 * @param confirmation answer for CONFIRMATION
	 */
	record IncomingMessage(String messageId, Kind kind, String from, String text, String mediaId, GeoPoint location,
			Boolean confirmation, Instant at) {
	}
}
