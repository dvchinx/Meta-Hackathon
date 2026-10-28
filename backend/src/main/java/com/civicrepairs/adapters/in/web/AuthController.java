package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.EmailVerificationUseCase;
import com.civicrepairs.application.port.in.GoogleSignInUseCase;
import com.civicrepairs.application.port.in.PasswordResetUseCase;
import com.civicrepairs.application.port.in.RegisterAccountUseCase;
import com.civicrepairs.application.port.in.SessionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** Email + password accounts. Session lives in an HttpOnly cookie; the JSON body never carries it. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final RegisterAccountUseCase register;
	private final SessionUseCase sessions;
	private final EmailVerificationUseCase verification;
	private final PasswordResetUseCase passwordReset;
	private final GoogleSignInUseCase googleSignIn;
	private final String googleClientId;

	AuthController(RegisterAccountUseCase register, SessionUseCase sessions, EmailVerificationUseCase verification,
			PasswordResetUseCase passwordReset, GoogleSignInUseCase googleSignIn,
			@Value("${civic.google.client-id:}") String googleClientId) {
		this.register = register;
		this.sessions = sessions;
		this.verification = verification;
		this.passwordReset = passwordReset;
		this.googleSignIn = googleSignIn;
		this.googleClientId = googleClientId;
	}

	/** Public settings the sign-in screens need. The Google client id is public by design. */
	@GetMapping("/config")
	ConfigResponse config() {
		return new ConfigResponse(googleClientId.isBlank() ? null : googleClientId);
	}

	@PostMapping("/google")
	ResponseEntity<MeResponse> google(@RequestBody GoogleRequest body, HttpServletRequest request) {
		return signedIn(googleSignIn.signIn(body.credential()), request);
	}

	@GetMapping("/me")
	ResponseEntity<MeResponse> me(Optional<AuthenticatedUser> user) {
		return ResponseEntity.ok(new MeResponse(user.map(UserResponse::from).orElse(null)));
	}

	@PostMapping("/register")
	ResponseEntity<MeResponse> register(@RequestBody RegisterRequest body, HttpServletRequest request) {
		String secret = register.register(body.email(), body.displayName(), body.password());
		return signedIn(secret, request);
	}

	@PostMapping("/login")
	ResponseEntity<MeResponse> login(@RequestBody LoginRequest body, HttpServletRequest request) {
		return signedIn(sessions.login(body.email(), body.password()), request);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		SessionCookies.read(request).ifPresent(sessions::logout);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessionCookies.clear(request).toString()).build();
	}

	@PostMapping("/verify-email")
	MeResponse verifyEmail(@RequestBody TokenRequest body) {
		return new MeResponse(UserResponse.from(verification.verify(body.token())));
	}

	@PostMapping("/resend-verification")
	ResponseEntity<Void> resendVerification(AuthenticatedUser user) {
		verification.resend(user.id());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset")
	ResponseEntity<Void> requestPasswordReset(@RequestBody EmailRequest body) {
		passwordReset.request(body.email());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset/confirm")
	ResponseEntity<Void> confirmPasswordReset(@RequestBody ResetRequest body) {
		passwordReset.reset(body.token(), body.password());
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<MeResponse> signedIn(String secret, HttpServletRequest request) {
		AuthenticatedUser user = sessions.resolve(secret).orElseThrow();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, SessionCookies.create(secret, request).toString())
				.body(new MeResponse(UserResponse.from(user)));
	}

	record RegisterRequest(String email, String displayName, String password) {
	}

	record GoogleRequest(String credential) {
	}

	record ConfigResponse(String googleClientId) {
	}

	record LoginRequest(String email, String password) {
	}

	record TokenRequest(String token) {
	}

	record EmailRequest(String email) {
	}

	record ResetRequest(String token, String password) {
	}

	/** @param user null for anonymous visitors */
	record MeResponse(UserResponse user) {
	}

	record UserResponse(String id, String email, String displayName, boolean emailVerified) {

		static UserResponse from(AuthenticatedUser u) {
			return new UserResponse(u.id().toString(), u.email(), u.displayName(), u.emailVerified());
		}
	}
}
