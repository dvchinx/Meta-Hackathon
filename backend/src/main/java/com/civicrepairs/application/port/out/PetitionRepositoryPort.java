package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.petition.Petition;

import java.util.Optional;
import java.util.UUID;

/** The petitioner snapshot is stored encrypted, like the filer profile. */
public interface PetitionRepositoryPort {

	/** Next number for the radicado. Gaps are fine; repeats are not. */
	long nextSequence();

	void save(Petition petition);

	Optional<Petition> findById(UUID id);
}
