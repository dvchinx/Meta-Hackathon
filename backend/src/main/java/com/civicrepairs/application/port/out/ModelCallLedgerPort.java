package com.civicrepairs.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cache and cost log of every model call. The cache key is a hash of the media + prompt version, so the same
 * input never reaches a model twice. Failed calls are recorded too: they cost money.
 */
public interface ModelCallLedgerPort {

	Optional<String> cachedResponse(String cacheKey);

	void record(ModelCall call);

	BigDecimal totalSpentUsd();

	/** @param response the raw successful output (JSON), or null when the call failed */
	record ModelCall(String cacheKey, String purpose, String model, String response, int inputTokens,
			int outputTokens, int images, BigDecimal costUsd, boolean success) {
	}
}
