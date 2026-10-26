package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.shared.DamageType;

import java.util.Optional;

public interface AgencyRepositoryPort {

	/** Empty when no rule covers the damage type (e.g. OTHER): the Issue goes to manual review. */
	Optional<Agency> findResponsibleFor(DamageType damageType);
}
