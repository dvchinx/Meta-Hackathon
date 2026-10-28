package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.PetitionRepositoryPort;
import com.civicrepairs.domain.petition.Petition;
import com.civicrepairs.domain.petition.Petitioner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Component
class PetitionPersistenceAdapter implements PetitionRepositoryPort {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final PersonalDataCipher cipher;

	PetitionPersistenceAdapter(JdbcClient jdbc, JsonMapper json, PersonalDataCipher cipher) {
		this.jdbc = jdbc;
		this.json = json;
		this.cipher = cipher;
	}

	@Override
	public long nextSequence() {
		return jdbc.sql("SELECT nextval('petition_seq')").query(Long.class).single();
	}

	@Override
	public void save(Petition p) {
		byte[] filer = cipher.encrypt(json.writeValueAsBytes(p.petitioner()), context(p.id()));
		jdbc.sql("""
						INSERT INTO petition (id, issue_id, agency_code, filer_user_id, radicado, filed_at, due_date,
						                      subject, filer_data, consent_policy_version)
						VALUES (:id, :issue, :agency, :filer, :radicado, :filedAt, :dueDate, :subject, :filerData, :version)
						""")
				.param("id", p.id())
				.param("issue", p.issueId())
				.param("agency", p.agencyCode())
				.param("filer", p.filerUserId())
				.param("radicado", p.radicado())
				.param("filedAt", Timestamp.from(p.filedAt()))
				.param("dueDate", Date.valueOf(p.dueDate()))
				.param("subject", p.subject())
				.param("filerData", filer)
				.param("version", p.policyVersion())
				.update();
	}

	@Override
	public Optional<Petition> findById(UUID id) {
		return jdbc.sql("SELECT * FROM petition WHERE id = :id").param("id", id).query(this::map).optional();
	}

	private Petition map(ResultSet rs, int row) throws SQLException {
		UUID id = rs.getObject("id", UUID.class);
		Petitioner petitioner = json.readValue(cipher.decrypt(rs.getBytes("filer_data"), context(id)), Petitioner.class);
		return new Petition(id,
				rs.getObject("issue_id", UUID.class),
				rs.getString("agency_code"),
				rs.getObject("filer_user_id", UUID.class),
				rs.getString("radicado"),
				rs.getTimestamp("filed_at").toInstant(),
				rs.getDate("due_date").toLocalDate(),
				petitioner,
				rs.getString("subject"),
				rs.getString("consent_policy_version"));
	}

	private static String context(UUID petitionId) {
		return "petition:" + petitionId;
	}
}
