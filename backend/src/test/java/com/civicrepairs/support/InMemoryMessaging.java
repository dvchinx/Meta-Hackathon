package com.civicrepairs.support;

import com.civicrepairs.application.port.out.MessagingPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory fake of the messaging port: records every outgoing message. */
public class InMemoryMessaging implements MessagingPort {

	public record Sent(String to, String kind, String text) {
	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	@Override
	public void sendText(String to, String text) {
		sent.add(new Sent(to, "text", text));
	}

	@Override
	public void sendImage(String to, byte[] jpeg, String caption) {
		sent.add(new Sent(to, "image", caption));
	}

	@Override
	public void sendConfirmationRequest(String to, String question) {
		sent.add(new Sent(to, "buttons", question));
	}

	@Override
	public DownloadedMedia downloadMedia(String mediaId) {
		return new DownloadedMedia(("photo-" + mediaId).getBytes(), "image/jpeg");
	}

	public List<Sent> sent() {
		return List.copyOf(sent);
	}

	public void clear() {
		sent.clear();
	}
}
