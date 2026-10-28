package com.civicrepairs.adapters.out.google;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/** Tokens signed with a local key stand in for Google's: nothing leaves the machine. */
class GoogleIdTokenVerifierTest {

	private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
	private static RSAKey googleKey;
	private static RSAKey otherKey;
	private static GoogleIdTokenVerifier verifier;

	@BeforeAll
	static void keys() throws JOSEException {
		googleKey = new RSAKeyGenerator(2048).keyID("google-1").generate();
		otherKey = new RSAKeyGenerator(2048).keyID("google-1").generate();
		verifier = new GoogleIdTokenVerifier(CLIENT_ID, new ImmutableJWKSet<>(new JWKSet(googleKey.toPublicJWK())));
	}

	private static String token(RSAKey key, UnaryOperator<JWTClaimsSet.Builder> customize) throws JOSEException {
		Instant now = Instant.now();
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
				.issuer("https://accounts.google.com")
				.audience(CLIENT_ID)
				.subject("1234567890")
				.claim("email", "ana@gmail.com")
				.claim("email_verified", true)
				.claim("name", "Ana María")
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(3600)));
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
				customize.apply(claims).build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	@Test
	void acceptsAValidToken() throws JOSEException {
		var identity = verifier.verify(token(googleKey, c -> c)).orElseThrow();

		assertThat(identity.subject()).isEqualTo("1234567890");
		assertThat(identity.email()).isEqualTo("ana@gmail.com");
		assertThat(identity.emailVerified()).isTrue();
		assertThat(identity.name()).isEqualTo("Ana María");
	}

	@Test
	void acceptsTheShortIssuerToo() throws JOSEException {
		assertThat(verifier.verify(token(googleKey, c -> c.issuer("accounts.google.com")))).isPresent();
	}

	@Test
	void rejectsTokensForAnotherApp() throws JOSEException {
		assertThat(verifier.verify(token(googleKey, c -> c.audience("someone-else.apps.googleusercontent.com"))))
				.isEmpty();
	}

	@Test
	void rejectsOtherIssuers() throws JOSEException {
		assertThat(verifier.verify(token(googleKey, c -> c.issuer("https://evil.example")))).isEmpty();
	}

	@Test
	void rejectsExpiredTokens() throws JOSEException {
		Instant past = Instant.now().minusSeconds(7200);
		assertThat(verifier.verify(token(googleKey, c -> c.issueTime(Date.from(past))
				.expirationTime(Date.from(past.plusSeconds(600)))))).isEmpty();
	}

	@Test
	void rejectsTokensNotSignedByGoogle() throws JOSEException {
		assertThat(verifier.verify(token(otherKey, c -> c))).isEmpty();
	}

	@Test
	void rejectsGarbageAndMissingEmail() throws JOSEException {
		assertThat(verifier.verify("not.a.jwt")).isEmpty();
		assertThat(verifier.verify(token(googleKey, c -> c.claim("email", null)))).isEmpty();
	}

	@Test
	void reportsUnverifiedEmails() throws JOSEException {
		var identity = verifier.verify(token(googleKey, c -> c.claim("email_verified", false))).orElseThrow();
		assertThat(identity.emailVerified()).isFalse();
	}
}
