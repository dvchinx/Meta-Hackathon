package com.civicrepairs.application.port.in;

import java.util.UUID;

/** The signed-in citizen, as inbound adapters see them. */
public record AuthenticatedUser(UUID id, String email, String displayName, boolean emailVerified) {
}
