package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.PublicIssue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read model for the public map: a flat projection, no aggregate loading. */
public interface PublicIssueReadPort {

	List<PublicIssue> findAllPublic();

	Optional<PublicIssue> findPublic(UUID id);
}
