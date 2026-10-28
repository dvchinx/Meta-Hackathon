package com.civicrepairs.application.port.in;

/**
 * Where a citizen can report the damage for real (our filing is simulated).
 *
 * @param phone e.g. "Acualínea 116"; null when there is no verified line
 */
public record OfficialChannel(String url, String phone) {
}
