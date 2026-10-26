package com.civicrepairs.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface StatusTransitionJpaRepository extends JpaRepository<StatusTransitionJpaEntity, Long> {
}
