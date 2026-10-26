package com.civicrepairs.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface IssueJpaRepository extends JpaRepository<IssueJpaEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from IssueJpaEntity i where i.id = :id")
	Optional<IssueJpaEntity> findByIdForUpdate(UUID id);
}
