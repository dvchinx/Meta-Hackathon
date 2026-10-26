package com.civicrepairs.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only: created once, never updated. */
@Entity
@Table(name = "status_transition")
class StatusTransitionJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "issue_id", updatable = false)
	IssueJpaEntity issue;

	@Column(nullable = false, updatable = false)
	int seq;

	@Column(name = "from_status", updatable = false)
	String fromStatus;

	@Column(name = "to_status", nullable = false, updatable = false)
	String toStatus;

	@Column(nullable = false, updatable = false)
	String cause;

	@Column(name = "evidence_ref", nullable = false, updatable = false)
	String evidenceRef;

	@Column(nullable = false, updatable = false)
	Instant at;
}
