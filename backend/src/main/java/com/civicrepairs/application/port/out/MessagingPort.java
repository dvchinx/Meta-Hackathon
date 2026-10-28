package com.civicrepairs.application.port.out;

import java.util.List;

/**
 * Outbound chat channel (WhatsApp). {@code to} is the raw phone number: it is only passed through to send a reply
 * and never stored.
 */
public interface MessagingPort {

	String CONFIRM_YES = "confirm_yes";
	String CONFIRM_NO = "confirm_no";

	void sendText(String to, String text);

	void sendImage(String to, byte[] jpeg, String caption);

	/** A question with "Sí" / "No" reply buttons ({@link #CONFIRM_YES} / {@link #CONFIRM_NO}). */
	void sendConfirmationRequest(String to, String question);

	DownloadedMedia downloadMedia(String mediaId);

	/**
	 * A pre-approved template (docs/whatsapp-templates.md): the only kind of message WhatsApp delivers outside the
	 * 24-hour window. Best effort, like every send.
	 */
	void sendTemplate(String to, String template, List<String> parameters);

	record DownloadedMedia(byte[] data, String contentType) {
	}
}
