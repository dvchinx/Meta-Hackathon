package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;

import java.util.Optional;
import java.util.UUID;

public interface MediaStoragePort {

	Media store(byte[] data, String contentType, MediaKind kind, Visibility visibility, UUID derivedFrom);

	MediaContent load(UUID id);

	/** Only PUBLIC media; empty for unknown or private ids, so callers cannot tell them apart. */
	Optional<MediaContent> loadPublic(UUID id);
}
