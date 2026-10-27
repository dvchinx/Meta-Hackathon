package com.civicrepairs.support;

import com.civicrepairs.application.port.out.ModelCallLedgerPort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory fake of the ledger port. */
public class InMemoryModelCallLedger implements ModelCallLedgerPort {

	private final List<ModelCall> calls = new ArrayList<>();

	@Override
	public Optional<String> cachedResponse(String cacheKey) {
		return calls.stream().filter(c -> c.success() && c.cacheKey().equals(cacheKey)).map(ModelCall::response)
				.findFirst();
	}

	@Override
	public void record(ModelCall call) {
		calls.add(call);
	}

	@Override
	public BigDecimal totalSpentUsd() {
		return calls.stream().map(ModelCall::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public List<ModelCall> calls() {
		return List.copyOf(calls);
	}
}
