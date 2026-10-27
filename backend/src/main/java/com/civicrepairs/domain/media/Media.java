package com.civicrepairs.domain.media;

import java.util.Objects;
import java.util.UUID;

/**
 * Stored media metadata.
 *
 * @param sha256      content hash; part of every model cache key
 * @param derivedFrom original this copy was produced from (e.g. the blurred version of a photo), or null
 */
public record Media(UUID id, MediaKind kind, Visibility visibility, String contentType, String sha256, long sizeBytes,
		UUID derivedFrom) {

	public Media {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(visibility, "visibility");
		Objects.requireNonNull(contentType, "contentType");
		Objects.requireNonNull(sha256, "sha256");
	}
}
