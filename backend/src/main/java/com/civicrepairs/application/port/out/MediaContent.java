package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.media.Media;

/** Media metadata plus its bytes, as handed to model adapters. */
public record MediaContent(Media media, byte[] data) {
}
