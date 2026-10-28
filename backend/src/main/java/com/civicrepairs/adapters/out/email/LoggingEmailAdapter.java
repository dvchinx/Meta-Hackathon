package com.civicrepairs.adapters.out.email;

import com.civicrepairs.application.port.out.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Offline stand-in when RESEND_API_KEY is not set: the email (and its link) goes to the log. */
@Component
@ConditionalOnExpression("'${civic.email.resend-api-key:}'.isBlank()")
class LoggingEmailAdapter implements EmailPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

	@Override
	public void send(String to, String subject, String text, String html) {
		log.info("[email → {}] {}\n{}", to, subject, text);
	}
}
