package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.domain.issue.IssueStatus;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What followers hear when an Issue changes status: an email for web accounts and an approved WhatsApp template
 * (texts in docs/whatsapp-templates.md; keep the parameters in sync). Colombian Spanish, short.
 */
final class IssueNotices {

	private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-CO"));

	private IssueNotices() {
	}

	/**
	 * @param subject  email subject
	 * @param text     email body (plain text, the HTML version adds a button)
	 * @param action   email button label
	 * @param template WhatsApp template name
	 * @param params   template body parameters, in order
	 */
	record Notice(String subject, String text, String action, String template, List<String> params) {
	}

	/**
	 * @param agencySummary latest agency reply, for AGENCY_RESPONDED
	 * @return empty for statuses nobody needs to hear about (REPORTED)
	 */
	static Optional<Notice> forStatus(IssueStatus status, PublicIssue issue, String agencySummary, String link) {
		String damage = damage(issue);
		String agency = issue.agencyName() == null ? "La entidad" : issue.agencyName();
		return Optional.ofNullable(switch (status) {
			case FILED -> issue.petition() == null ? null : new Notice(
					"Se radicó la petición: " + damage,
					"Se radicó una petición por el daño que sigues (" + damage + "). Radicado "
							+ issue.petition().radicado() + ". " + agency + " tiene hasta el "
							+ DUE.format(issue.petition().dueDate()) + " para responder.",
					"Ver el daño", "aviso_peticion_radicada",
					List.of(damage, issue.petition().radicado(), DUE.format(issue.petition().dueDate()), link));
			case AGENCY_RESPONDED -> new Notice(
					agency + " respondió: " + damage,
					agency + " respondió sobre el daño que sigues (" + damage + "): " + oneLine(agencySummary),
					"Ver la respuesta", "aviso_entidad_respondio",
					List.of(agency, damage, oneLine(agencySummary), link));
			case REPAIR_CLAIMED -> new Notice(
					"¿Repararon el daño? " + damage,
					agency + " dice que ya reparó el daño que sigues (" + damage + "). Solo lo damos por reparado "
							+ "cuando una foto lo confirma: si pasas por el lugar, envíanos una foto actual.",
					"Enviar foto de verificación", "aviso_reparacion_reportada", List.of(agency, damage));
			case VERIFIED -> new Notice(
					"Reparación verificada: " + damage,
					"Una foto ciudadana confirmó que el daño que sigues (" + damage + ") quedó reparado. "
							+ "¡Gracias por ayudar!",
					"Ver el antes y después", "aviso_reparacion_verificada", List.of(damage, link));
			case REOPENED -> new Notice(
					"El daño sigue ahí: " + damage,
					"Una foto muestra que el daño que sigues (" + damage + ") no está reparado, aunque " + agency
							+ " dijo que sí. Lo reabrimos para volver a exigir la reparación.",
					"Ver el daño", "aviso_dano_reabierto", List.of(damage, link));
			case NEEDS_MORE_EVIDENCE -> new Notice(
					"Necesitamos otra foto: " + damage,
					"No pudimos confirmar con la última foto si el daño que sigues (" + damage + ") quedó reparado. "
							+ "Si pasas por el lugar, envíanos otra desde un ángulo parecido al original.",
					"Enviar otra foto", "aviso_falta_foto", List.of(damage));
			case REPORTED -> null;
		});
	}

	static String damage(PublicIssue issue) {
		String label = BotMessages.label(issue.damageType());
		return issue.localityName() == null ? label : label + " en " + issue.localityName();
	}

	/** WhatsApp template parameters cannot contain line breaks or long runs of spaces. */
	static String oneLine(String text) {
		return text == null || text.isBlank() ? "sin detalles" : text.strip().replaceAll("\\s+", " ");
	}
}
