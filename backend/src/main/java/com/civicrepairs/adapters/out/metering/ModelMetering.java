package com.civicrepairs.adapters.out.metering;

import com.civicrepairs.application.port.out.ModelCallLedgerPort;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.config.MetaApiProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Cache keys and the hard budget cap shared by every real model adapter. */
@Component
public class ModelMetering {

	private final ModelCallLedgerPort ledger;
	private final MetaApiProperties properties;

	public ModelMetering(ModelCallLedgerPort ledger, MetaApiProperties properties) {
		this.ledger = ledger;
		this.properties = properties;
	}

	/** Call before every paid request. */
	public void ensureBudget() {
		var spent = ledger.totalSpentUsd();
		if (spent.compareTo(properties.budgetLimitUsd()) >= 0) {
			throw new ModelUnavailableException(ModelUnavailableException.Reason.BUDGET_EXHAUSTED,
					"spent " + spent + " USD of " + properties.budgetLimitUsd());
		}
	}

	/** Same parts → same key. Parts are media hashes, prompt versions, model ids, etc. */
	public static String cacheKey(String... parts) {
		return sha256(String.join("|", parts));
	}

	public static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
