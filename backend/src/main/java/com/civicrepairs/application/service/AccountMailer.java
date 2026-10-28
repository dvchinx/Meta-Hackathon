package com.civicrepairs.application.service;

import com.civicrepairs.application.port.out.EmailPort;
import com.civicrepairs.application.port.out.EmailTokenRepositoryPort;
import com.civicrepairs.domain.account.EmailTokenPurpose;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Builds and sends the two account emails. Links point at the web app's hash routes. */
@Component
class AccountMailer {

	static final Duration VERIFY_TTL = Duration.ofHours(24);
	static final Duration RESET_TTL = Duration.ofHours(1);

	private final EmailPort email;
	private final EmailTokenRepositoryPort tokens;
	private final Clock clock;
	private final String baseUrl;

	AccountMailer(EmailPort email, EmailTokenRepositoryPort tokens, Clock clock,
			@Value("${civic.public-base-url}") String publicBaseUrl) {
		this.email = email;
		this.tokens = tokens;
		this.clock = clock;
		this.baseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
	}

	void sendVerification(UserAccount account) {
		String link = link("verificar", issue(account.id(), EmailTokenPurpose.VERIFY_EMAIL, VERIFY_TTL));
		String text = """
				Hola, %s.

				Confirma tu correo para empezar a reportar daños en Bogotá:
				%s

				El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este mensaje.
				""".formatted(account.displayName(), link);
		email.send(account.email(), "Confirma tu correo", text, html("Confirma tu correo",
				"Hola, " + escape(account.displayName()) + ". Confirma tu correo para empezar a reportar daños en Bogotá.",
				link, "Confirmar correo", "El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este mensaje."));
	}

	void sendPasswordReset(UserAccount account) {
		String link = link("restablecer", issue(account.id(), EmailTokenPurpose.RESET_PASSWORD, RESET_TTL));
		String text = """
				Hola, %s.

				Para elegir una contraseña nueva entra aquí:
				%s

				El enlace vence en 1 hora. Si no pediste este cambio, ignora este mensaje: tu contraseña sigue igual.
				""".formatted(account.displayName(), link);
		email.send(account.email(), "Restablece tu contraseña", text, html("Restablece tu contraseña",
				"Hola, " + escape(account.displayName()) + ". Para elegir una contraseña nueva usa el botón.",
				link, "Elegir contraseña nueva",
				"El enlace vence en 1 hora. Si no pediste este cambio, ignora este mensaje: tu contraseña sigue igual."));
	}

	private String issue(UUID userId, EmailTokenPurpose purpose, Duration ttl) {
		String secret = Secrets.generate();
		tokens.store(Secrets.hash(secret), userId, purpose, clock.instant().plus(ttl));
		return secret;
	}

	private String link(String route, String token) {
		return baseUrl + "/#/" + route + "?token=" + token;
	}

	private static String html(String title, String intro, String link, String action, String footer) {
		return """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;color:#101828">
				  <h2 style="font-size:20px">%s</h2>
				  <p>%s</p>
				  <p><a href="%s" style="display:inline-block;padding:10px 16px;background:#b42318;color:#fff;\
				text-decoration:none;border-radius:8px;font-weight:600">%s</a></p>
				  <p style="color:#475467;font-size:14px">%s</p>
				  <p style="color:#667085;font-size:12px">Si el botón no funciona, copia este enlace:<br>%s</p>
				</div>
				""".formatted(escape(title), intro, link, escape(action), escape(footer), link);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
