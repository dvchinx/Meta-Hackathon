package com.civicrepairs.adapters.out.muse;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** A versioned prompt from {@code resources/prompts}: {@code <name>.md} plus {@code <name>.schema.json}. */
record MusePrompt(String version, String instructions, String schemaJson) {

	static MusePrompt load(String version) {
		return new MusePrompt(version, read("prompts/" + version + ".md"), read("prompts/" + version + ".schema.json"));
	}

	private static String read(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("missing prompt resource " + path, e);
		}
	}
}
