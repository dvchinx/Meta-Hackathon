package com.civicrepairs.application.port.out;

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

	record DownloadedMedia(byte[] data, String contentType) {
	}
}
