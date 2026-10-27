package com.civicrepairs.adapters.out.whatsapp;

import com.civicrepairs.application.port.out.MessagingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Offline stand-in when WHATSAPP_ACCESS_TOKEN is not set: replies go to the log (with masked numbers). */
@Component
@ConditionalOnExpression("'${civic.whatsapp.access-token:}'.isBlank()")
class LoggingMessagingAdapter implements MessagingPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingMessagingAdapter.class);

	@Override
	public void sendText(String to, String text) {
		log.info("[whatsapp → {}] {}", PhoneMask.mask(to), text);
	}

	@Override
	public void sendImage(String to, byte[] jpeg, String caption) {
		log.info("[whatsapp → {}] <image {} bytes> {}", PhoneMask.mask(to), jpeg.length, caption);
	}

	@Override
	public void sendConfirmationRequest(String to, String question) {
		log.info("[whatsapp → {}] {} [Sí | No]", PhoneMask.mask(to), question);
	}

	@Override
	public DownloadedMedia downloadMedia(String mediaId) {
		throw new IllegalStateException("WhatsApp is not configured (WHATSAPP_ACCESS_TOKEN): cannot download " + mediaId);
	}
}
