package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class AgencyPersistenceAdapter implements AgencyRepositoryPort {

	private final JdbcClient jdbc;

	AgencyPersistenceAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Agency> findResponsibleFor(DamageType damageType) {
		return jdbc.sql("""
						SELECT a.code, a.name, a.petition_email
						FROM agency_rule r JOIN agency a ON a.code = r.agency_code
						WHERE r.damage_type = :type
						""")
				.param("type", damageType.name())
				.query((rs, n) -> new Agency(rs.getString("code"), rs.getString("name"), rs.getString("petition_email")))
				.optional();
	}
}
