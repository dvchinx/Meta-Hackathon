package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.FilerProfileRepositoryPort;
import com.civicrepairs.domain.petition.FilerProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class FilerProfilePersistenceAdapter implements FilerProfileRepositoryPort {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final PersonalDataCipher cipher;

	FilerProfilePersistenceAdapter(JdbcClient jdbc, JsonMapper json, PersonalDataCipher cipher) {
		this.jdbc = jdbc;
		this.json = json;
		this.cipher = cipher;
	}

	@Override
	public Optional<FilerProfile> find(UUID userId) {
		return jdbc.sql("SELECT data FROM filer_profile WHERE user_id = :user")
				.param("user", userId)
				.query(byte[].class)
				.optional()
				.map(data -> json.readValue(cipher.decrypt(data, context(userId)), FilerProfile.class));
	}

	@Override
	public void save(UUID userId, FilerProfile profile, String policyVersion, Instant at) {
		byte[] data = cipher.encrypt(json.writeValueAsBytes(profile), context(userId));
		jdbc.sql("""
						INSERT INTO filer_profile (user_id, data, consent_policy_version, consented_at, updated_at)
						VALUES (:user, :data, :version, :at, :at)
						ON CONFLICT (user_id) DO UPDATE SET
						    data = EXCLUDED.data, consent_policy_version = EXCLUDED.consent_policy_version,
						    consented_at = EXCLUDED.consented_at, updated_at = EXCLUDED.updated_at
						""")
				.param("user", userId)
				.param("data", data)
				.param("version", policyVersion)
				.param("at", Timestamp.from(at))
				.update();
	}

	@Override
	public void delete(UUID userId) {
		jdbc.sql("DELETE FROM filer_profile WHERE user_id = :user").param("user", userId).update();
	}

	private static String context(UUID userId) {
		return "filer_profile:" + userId;
	}
}
