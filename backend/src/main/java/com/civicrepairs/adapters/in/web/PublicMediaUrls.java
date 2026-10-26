package com.civicrepairs.adapters.in.web;

import java.util.UUID;

/** URLs of public (blurred) media. Original photos have no public URL at all. */
final class PublicMediaUrls {

	private PublicMediaUrls() {
	}

	static String of(UUID mediaId) {
		return "/media/" + mediaId + ".jpg";
	}
}
