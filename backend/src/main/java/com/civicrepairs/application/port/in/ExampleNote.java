package com.civicrepairs.application.port.in;

/**
 * Marks an Issue loaded as a demo example (its photos are AI edits, not a citizen's report).
 *
 * @param credit attribution of the base photo, e.g. "Foto base: usuario / Mapillary (CC BY-SA 4.0)"; may be null
 */
public record ExampleNote(String credit) {
}
