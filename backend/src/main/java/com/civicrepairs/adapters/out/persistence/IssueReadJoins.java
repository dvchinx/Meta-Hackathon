package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.FiledPetition;
import com.civicrepairs.application.port.in.OfficialChannel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Joins shared by the Issue read models, for an {@code issue i}: agency name, locality (by boundary), official
 * channel (the agency's own row wins over the damage type's default) and the latest petition.
 */
final class IssueReadJoins {

	static final String COLUMNS = """
			a.name AS agency_name, loc.name AS locality_name, ch.url AS channel_url, ch.phone AS channel_phone,
			pet.radicado, pet.filed_at, pet.due_date
			""";

	static final String JOINS = """
			LEFT JOIN agency a ON a.code = i.agency_code
			LEFT JOIN LATERAL (
			    SELECT name FROM locality WHERE ST_Covers(boundary, i.location::geometry) ORDER BY code LIMIT 1
			) loc ON true
			LEFT JOIN LATERAL (
			    SELECT url, phone FROM official_channel oc
			    WHERE oc.damage_type = i.damage_type AND (oc.agency_code = i.agency_code OR oc.agency_code IS NULL)
			    ORDER BY oc.agency_code IS NULL
			    LIMIT 1
			) ch ON true
			LEFT JOIN LATERAL (
			    SELECT radicado, filed_at, due_date FROM petition WHERE issue_id = i.id ORDER BY filed_at DESC LIMIT 1
			) pet ON true
			""";

	private IssueReadJoins() {
	}

	static OfficialChannel channel(ResultSet rs) throws SQLException {
		String url = rs.getString("channel_url");
		return url == null ? null : new OfficialChannel(url, rs.getString("channel_phone"));
	}

	static FiledPetition petition(ResultSet rs) throws SQLException {
		String radicado = rs.getString("radicado");
		if (radicado == null) {
			return null;
		}
		Timestamp filedAt = rs.getTimestamp("filed_at");
		return new FiledPetition(radicado, filedAt.toInstant(), rs.getDate("due_date").toLocalDate());
	}
}
