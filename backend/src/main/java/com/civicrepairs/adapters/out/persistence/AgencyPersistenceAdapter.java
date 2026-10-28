package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Component
class AgencyPersistenceAdapter implements AgencyRepositoryPort {

	private final JdbcClient jdbc;

	AgencyPersistenceAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Agency> findResponsibleFor(DamageType damageType, AssetOwner owner, String localityCode) {
		return jdbc.sql("""
						SELECT a.code, a.name, a.petition_email
						FROM agency_rule r
						LEFT JOIN locality l ON r.by_locality AND l.code = :locality
						JOIN agency a ON a.code = COALESCE(r.agency_code, l.local_mayor_agency_code)
						WHERE r.damage_type = :type AND r.asset_owner IN (:owner, 'ANY')
						ORDER BY r.asset_owner = 'ANY'
						LIMIT 1
						""")
				.param("type", damageType.name())
				.param("owner", owner.name())
				.param("locality", localityCode)
				.query(AgencyPersistenceAdapter::map)
				.optional();
	}

	@Override
	public Optional<Agency> findByCode(String code) {
		return jdbc.sql("SELECT code, name, petition_email FROM agency WHERE code = :code")
				.param("code", code)
				.query(AgencyPersistenceAdapter::map)
				.optional();
	}

	private static Agency map(ResultSet rs, int row) throws SQLException {
		return new Agency(rs.getString("code"), rs.getString("name"), rs.getString("petition_email"));
	}
}
