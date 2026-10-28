package com.civicrepairs.application.service;

import com.civicrepairs.domain.issue.VerificationResult;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.report.MissingItem;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;

import java.util.Set;

/** Everything the bot says. Colombian Spanish, short and clear. */
final class BotMessages {

	private BotMessages() {
	}

	static final String LOCATION_HOW = "Toca 📎 → Ubicación y mueve el pin al punto exacto del daño.";

	static String askFor(Set<MissingItem> missing, boolean firstContact) {
		String intro = firstContact ? "¡Hola! 👋 Gracias por ayudar a cuidar Bogotá.\n\n" : "";
		if (missing.containsAll(Set.of(MissingItem.PHOTO, MissingItem.LOCATION))) {
			return intro + "Para reportar un daño envíame:\n1. Una foto del daño 📷\n2. La ubicación 📍\n\n" + LOCATION_HOW;
		}
		if (missing.contains(MissingItem.PHOTO)) {
			return intro + "Listo, ya tengo la ubicación. Ahora envíame una foto del daño 📷";
		}
		return intro + "Recibí la foto 📷. Ahora envíame la ubicación 📍\n" + LOCATION_HOW;
	}

	static final String ANALYZING = "Recibí todo ✅ Estoy revisando la foto, dame unos segundos…";

	static final String STILL_ANALYZING = "Todavía estoy revisando tu foto. Te escribo en un momento.";

	static final String OUTSIDE_BOGOTA =
			"Esa ubicación está fuera de Bogotá. Por ahora solo recibimos reportes en Bogotá. " + LOCATION_HOW;

	static final String RATE_LIMITED =
			"Llegaste al límite de reportes de hoy. ¡Gracias por ayudar! Puedes enviar más mañana.";

	static final String PHOTO_REJECTED = "No logré identificar un daño en la vía pública en esa foto 🤔 "
			+ "¿Me envías otra, más cerca del daño y con buena luz? Ya tengo tu ubicación.";

	static final String MANUAL_REVIEW =
			"Gracias 🙌 Este caso lo va a revisar una persona de nuestro equipo y te avisaremos.";

	static final String MODELS_UNAVAILABLE = "Tuvimos un problema revisando la foto. Una persona de nuestro equipo "
			+ "revisará tu reporte y te avisaremos.";

	static final String UNSUPPORTED = "Por ahora solo entiendo fotos, texto y ubicación 🙏 "
			+ "Si enviaste un audio o video, cuéntame el problema por escrito.";

	static final String UNEXPECTED_ERROR = "Tuvimos un problema procesando tu mensaje 😕 Intenta de nuevo en un momento.";

	static final String ANSWER_YES_OR_NO = "¿La descripción es correcta? Responde con los botones Sí o No.";

	static final String CANCELLED = "Listo, descarté ese reporte. Si quieres reportar otro daño, envíame una foto.";

	static final String CALL_123 =
			"🚨 Si hay peligro inmediato para personas o vehículos, llama ya a la *Línea 123*. Este reporte no reemplaza "
					+ "esa llamada.";

	static String summary(Interpretation i, String agency) {
		String responsible = agency == null ? "" : "\nResponsable: " + agency;
		return "*" + label(i.damageType()) + "* · urgencia " + urgency(i.urgency()) + "\n" + i.summary() + responsible;
	}

	static final String CONFIRM_QUESTION = "¿Es correcto? Si confirmas, lo publicamos en el mapa y hacemos seguimiento.";

	static String confirmed(boolean newIssue, int reportCount, String mapUrl, String fileUrl) {
		String what = newIssue
				? "Tu reporte quedó registrado ✅ Es el primero de este daño."
				: "Tu reporte quedó registrado ✅ Otras personas ya habían reportado este daño: ahora son "
						+ reportCount + " reportes.";
		return what + "\n\nTe avisaremos por aquí cuando la entidad responda y cuando haya que verificar la reparación "
				+ "con una foto. Para eso guardamos tu número cifrado y lo borramos cuando el daño quede reparado. "
				+ "Escribe BAJA si no quieres avisos."
				+ "\nMapa: " + mapUrl
				+ "\n\n¿Quieres radicar la petición ante la entidad con tus datos? Hazlo desde la web: " + fileUrl;
	}

	static final String UNSUBSCRIBED =
			"Listo, ya no te enviaremos avisos y borramos tu número de nuestra lista. Si reportas otro daño, volverás a "
					+ "recibir avisos de ese daño.";

	static final String NOT_SUBSCRIBED = "No tenías avisos activos. Si quieres reportar un daño, envíame una foto 📷";

	static final String VERIFYING = "Recibí la foto 📷 La estoy comparando con la del reporte original, dame unos segundos…";

	static String verified(VerificationResult.Outcome outcome, String reason) {
		String detail = reason == null || reason.isBlank() ? "" : "\n" + reason;
		String next = "\n\nSi quieres reportar otro daño, envíame una foto nueva.";
		return switch (outcome) {
			case REPAIRED -> "¡Gracias! ✅ La foto confirma la reparación: el daño queda como *reparado verificado*."
					+ detail + next;
			case NOT_REPAIRED -> "Gracias. La foto muestra que el daño sigue ahí ❌ Lo reabrimos para volver a exigir "
					+ "la reparación." + detail + next;
			case INCONCLUSIVE -> "No pudimos confirmarlo con esta foto 🤔 ¿Me envías otra, desde un ángulo parecido al "
					+ "original y con buena luz?" + detail;
		};
	}

	static String label(DamageType type) {
		return switch (type) {
			case POTHOLE -> "Hueco";
			case OPEN_MANHOLE -> "Alcantarilla destapada";
			case DAMAGED_POLE -> "Poste dañado";
			case RISKY_TREE -> "Árbol en riesgo";
			case STREETLIGHT_OUT -> "Luminaria apagada";
			case WATER_LEAK -> "Fuga de agua";
			case BLOCKED_DRAIN -> "Sumidero tapado";
			case GARBAGE_DUMP -> "Basuras o escombros";
			case DAMAGED_SIDEWALK -> "Andén dañado";
			case DAMAGED_STREET_FURNITURE -> "Mobiliario urbano dañado";
			case OTHER -> "Otro daño";
		};
	}

	private static String urgency(Urgency urgency) {
		return switch (urgency) {
			case LOW -> "baja";
			case MEDIUM -> "media";
			case HIGH -> "alta ⚠️";
		};
	}
}
