package com.civicrepairs.support;

import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory fake of the media storage port. */
public class InMemoryMediaStorage implements MediaStoragePort {

	private final Map<UUID, MediaContent> media = new HashMap<>();

	@Override
	public Media store(byte[] data, String contentType, MediaKind kind, Visibility visibility, UUID derivedFrom) {
		var stored = new Media(UUID.randomUUID(), kind, visibility, contentType,
				HexFormat.of().formatHex(data), data.length, derivedFrom);
		media.put(stored.id(), new MediaContent(stored, data.clone()));
		return stored;
	}

	@Override
	public MediaContent load(UUID id) {
		return Optional.ofNullable(media.get(id)).orElseThrow();
	}

	@Override
	public Optional<MediaContent> loadPublic(UUID id) {
		return Optional.ofNullable(media.get(id)).filter(m -> m.media().visibility() == Visibility.PUBLIC);
	}
}
