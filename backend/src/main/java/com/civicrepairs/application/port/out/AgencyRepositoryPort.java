package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;

import java.util.Optional;

public interface AgencyRepositoryPort {

	/**
	 * The rule for the asset owner wins over the damage type's default. Rules that route by locality need
	 * {@code localityCode}.
	 *
	 * @return empty when no rule covers the damage type (e.g. OTHER): the Issue goes to manual review
	 */
	Optional<Agency> findResponsibleFor(DamageType damageType, AssetOwner owner, String localityCode);

	Optional<Agency> findByCode(String code);
}
