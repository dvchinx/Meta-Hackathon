package com.civicrepairs.support;

import com.civicrepairs.application.port.out.EmailPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory fake of the email port: records every message and can pull the link token out of it. */
public class InMemoryEmail implements EmailPort {

	public record Sent(String to, String subject, String text, String html) {
	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(String to, String subject, String text, String html) {
		sent.add(new Sent(to, subject, text, html));
	}

	public List<Sent> sent() {
		return List.copyOf(sent);
	}

	/** Token of the most recent link to {@code route} (e.g. "verificar") sent to the address. */
	public String lastToken(String to, String route) {
		Pattern pattern = Pattern.compile("#/" + route + "\\?token=([A-Za-z0-9_-]+)");
		for (int i = sent.size() - 1; i >= 0; i--) {
			Sent s = sent.get(i);
			if (!s.to().equals(to)) {
				continue;
			}
			Matcher m = pattern.matcher(s.text());
			if (m.find()) {
				return m.group(1);
			}
		}
		throw new AssertionError("no " + route + " link sent to " + to);
	}

	public void clear() {
		sent.clear();
	}
}
