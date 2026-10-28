package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ManageFilerProfileUseCase;
import com.civicrepairs.application.port.in.PetitionException;
import com.civicrepairs.application.port.in.PetitionException.Code;
import com.civicrepairs.application.port.in.PrivacyPolicy;
import com.civicrepairs.application.port.out.FilerProfileRepositoryPort;
import com.civicrepairs.domain.petition.FilerProfile;
import com.civicrepairs.domain.petition.IdentificationType;
import com.civicrepairs.domain.petition.InvalidFilerProfileException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class FilerProfileService implements ManageFilerProfileUseCase {

	private static final Map<String, String> FIELD_ERRORS = Map.of(
			"idType", "Elige el tipo de documento.",
			"idNumber", "Revisa el número de documento: solo números para cédula y tarjeta de identidad, "
					+ "letras y números para pasaporte o permisos.",
			"firstNames", "Escribe tus nombres como aparecen en tu documento.",
			"lastNames", "Escribe tus apellidos como aparecen en tu documento.",
			"mobilePhone", "Escribe un celular colombiano de 10 dígitos, por ejemplo 3001234567.",
			"address", "La dirección puede tener hasta " + FilerProfile.MAX_ADDRESS + " caracteres.");

	private final FilerProfileRepositoryPort profiles;
	private final Clock clock;

	FilerProfileService(FilerProfileRepositoryPort profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	@Override
	public Optional<FilerProfile> find(UUID userId) {
		return profiles.find(userId);
	}

	@Override
	public FilerProfile save(UUID userId, ProfileInput input, String policyVersion) {
		Policies.requireCurrent(policyVersion);
		FilerProfile profile;
		try {
			profile = new FilerProfile(idType(input.idType()), input.idNumber(), input.firstNames(), input.lastNames(),
					input.mobilePhone(), input.address());
		} catch (InvalidFilerProfileException e) {
			throw new PetitionException(Code.INVALID_PROFILE, FIELD_ERRORS.get(e.field()));
		}
		profiles.save(userId, profile, PrivacyPolicy.CURRENT_VERSION, clock.instant());
		return profile;
	}

	@Override
	public void delete(UUID userId) {
		profiles.delete(userId);
	}

	private static IdentificationType idType(String value) {
		if (value == null) {
			return null;
		}
		try {
			return IdentificationType.valueOf(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
