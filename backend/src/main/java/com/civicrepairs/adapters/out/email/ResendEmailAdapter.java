package com.civicrepairs.adapters.out.email;

import com.civicrepairs.application.port.out.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends through Resend's REST API. Failures are logged, never shown to the citizen. */
@Component
@ConditionalOnExpression("!'${civic.email.resend-api-key:}'.isBlank()")
class ResendEmailAdapter implements EmailPort {

	private static final Logger log = LoggerFactory.getLogger(ResendEmailAdapter.class);

	private final RestClient http;
	private final String from;

	ResendEmailAdapter(RestClient.Builder builder, @Value("${civic.email.resend-api-key}") String apiKey,
			@Value("${civic.email.from}") String from) {
		this.http = builder.clone()
				.baseUrl("https://api.resend.com")
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String text, String html) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", from);
		body.put("to", List.of(to));
		body.put("subject", subject);
		body.put("text", text);
		if (html != null) {
			body.put("html", html);
		}
		try {
			http.post().uri("/emails").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
		} catch (RestClientResponseException e) {
			log.error("Resend rejected '{}': {} {}", subject, e.getStatusCode(), e.getResponseBodyAsString());
		} catch (RestClientException e) {
			log.error("Resend request failed for '{}': {}", subject, e.getMessage());
		}
	}
}
