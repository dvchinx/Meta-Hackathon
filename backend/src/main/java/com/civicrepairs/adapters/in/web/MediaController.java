package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.out.MediaStoragePort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Serves PUBLIC (blurred) media only. Private and unknown ids both answer 404. */
@RestController
class MediaController {

	private final MediaStoragePort media;

	MediaController(MediaStoragePort media) {
		this.media = media;
	}

	@GetMapping("/media/{id}.jpg")
	ResponseEntity<byte[]> publicPhoto(@PathVariable UUID id) {
		return media.loadPublic(id)
				.map(content -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(content.media().contentType()))
						.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
						.body(content.data()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
