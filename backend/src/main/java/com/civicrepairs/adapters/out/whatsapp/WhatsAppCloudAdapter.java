package com.civicrepairs.adapters.out.whatsapp;

import com.civicrepairs.application.port.out.MessagingPort;
import com.civicrepairs.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Cloud API through Graph API. Sending is best effort (failures are logged, the conversation goes on);
 * downloading the citizen's photo is required, so it throws.
 */
@Component
@ConditionalOnExpression("!'${civic.whatsapp.access-token:}'.isBlank()")
class WhatsAppCloudAdapter implements MessagingPort {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudAdapter.class);
	private static final long MAX_MEDIA_BYTES = 16L * 1024 * 1024;
	private static final int MAX_CAPTION = 1024;

	private final RestClient graph;
	private final String phoneNumberId;

	WhatsAppCloudAdapter(RestClient.Builder builder, WhatsAppProperties properties) {
		this.graph = builder.clone()
				.baseUrl(properties.graphBaseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
				.build();
		this.phoneNumberId = properties.phoneNumberId();
	}

	@Override
	public void sendText(String to, String text) {
		send(to, "text", Map.of("type", "text", "text", Map.of("preview_url", true, "body", text)));
	}

	@Override
	public void sendImage(String to, byte[] jpeg, String caption) {
		String mediaId;
		try {
			mediaId = upload(jpeg);
		} catch (RestClientException e) {
			log.error("WhatsApp media upload failed: {}", details(e));
			sendText(to, caption);
			return;
		}
		String text = caption.length() > MAX_CAPTION ? caption.substring(0, MAX_CAPTION) : caption;
		send(to, "image", Map.of("type", "image", "image", Map.of("id", mediaId, "caption", text)));
	}

	@Override
	public void sendConfirmationRequest(String to, String question) {
		var buttons = List.of(
				Map.of("type", "reply", "reply", Map.of("id", CONFIRM_YES, "title", "Sí, es correcto")),
				Map.of("type", "reply", "reply", Map.of("id", CONFIRM_NO, "title", "No")));
		send(to, "buttons", Map.of("type", "interactive", "interactive", Map.of(
				"type", "button",
				"body", Map.of("text", question),
				"action", Map.of("buttons", buttons))));
	}

	@Override
	public DownloadedMedia downloadMedia(String mediaId) {
		JsonNode info = graph.get().uri("/{id}", mediaId).retrieve().body(JsonNode.class);
		long size = info.path("file_size").asLong(0);
		if (size > MAX_MEDIA_BYTES) {
			throw new IllegalArgumentException("media too large: " + size + " bytes");
		}
		byte[] data = graph.get().uri(URI.create(info.path("url").asString())).retrieve().body(byte[].class);
		return new DownloadedMedia(data, info.path("mime_type").asString("image/jpeg"));
	}

	private String upload(byte[] jpeg) {
		var fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.IMAGE_JPEG);
		fileHeaders.setContentDispositionFormData("file", "reporte.jpg");
		var form = new LinkedMultiValueMap<String, Object>();
		form.add("messaging_product", "whatsapp");
		form.add("type", "image/jpeg");
		form.add("file", new HttpEntity<>(new ByteArrayResource(jpeg), fileHeaders));
		JsonNode response = graph.post()
				.uri("/{phone}/media", phoneNumberId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(form)
				.retrieve()
				.body(JsonNode.class);
		return response.path("id").asString();
	}

	private void send(String to, String what, Map<String, Object> content) {
		var body = new LinkedHashMap<String, Object>();
		body.put("messaging_product", "whatsapp");
		body.put("recipient_type", "individual");
		body.put("to", to);
		body.putAll(content);
		try {
			graph.post().uri("/{phone}/messages", phoneNumberId)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			log.error("WhatsApp {} to {} failed: {}", what, PhoneMask.mask(to), details(e));
		}
	}

	private static String details(RestClientException e) {
		return e instanceof RestClientResponseException r ? r.getStatusCode() + " " + r.getResponseBodyAsString()
				: e.getMessage();
	}
}
