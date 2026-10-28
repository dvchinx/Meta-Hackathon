package com.civicrepairs.adapters.out.google;

import com.civicrepairs.application.port.out.GoogleIdentityPort;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a Google ID token: RS256 signature against Google's published keys (cached), audience = our client id,
 * issuer = Google, not expired. https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
 */
@Component
@ConditionalOnExpression("!'${civic.google.client-id:}'.isBlank()")
class GoogleIdTokenVerifier implements GoogleIdentityPort {

	private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
	private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
	private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

	@Autowired
	GoogleIdTokenVerifier(@Value("${civic.google.client-id}") String clientId) throws MalformedURLException {
		this(clientId, JWKSourceBuilder.create(URI.create(GOOGLE_KEYS).toURL()).retrying(true).build());
	}

	GoogleIdTokenVerifier(String clientId, JWKSource<SecurityContext> keys) {
		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
		processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(clientId, new JWTClaimsSet.Builder().build(),
				Set.of("sub", "email", "iss", "exp", "iat")));
	}

	@Override
	public Optional<GoogleIdentity> verify(String idToken) {
		try {
			JWTClaimsSet claims = processor.process(idToken, null);
			if (!ISSUERS.contains(claims.getIssuer())) {
				return Optional.empty();
			}
			Boolean verified = claims.getBooleanClaim("email_verified");
			return Optional.of(new GoogleIdentity(claims.getSubject(), claims.getStringClaim("email"),
					Boolean.TRUE.equals(verified), claims.getStringClaim("name")));
		} catch (ParseException | BadJOSEException e) {
			log.info("Rejected Google ID token: {}", e.getMessage());
			return Optional.empty();
		} catch (JOSEException e) {
			log.warn("Could not check Google ID token (keys unavailable?): {}", e.getMessage());
			return Optional.empty();
		}
	}
}
