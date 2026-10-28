package com.civicrepairs.application.port.out;

/** Transactional email (verification links, password resets). Never marketing. */
public interface EmailPort {

	/** @param html optional; implementations fall back to {@code text} */
	void send(String to, String subject, String text, String html);
}
