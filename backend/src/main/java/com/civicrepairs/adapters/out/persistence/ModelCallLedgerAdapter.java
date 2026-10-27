package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.ModelCallLedgerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;

@Component
class ModelCallLedgerAdapter implements ModelCallLedgerPort {

	private final JdbcClient jdbc;
	private final Clock clock;

	ModelCallLedgerAdapter(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Optional<String> cachedResponse(String cacheKey) {
		return jdbc.sql("SELECT response FROM model_call WHERE cache_key = :key AND success")
				.param("key", cacheKey)
				.query(String.class)
				.optional();
	}

	@Override
	public void record(ModelCall call) {
		// A concurrent identical call may have cached the same key first: keep its answer, still log our cost.
		jdbc.sql("""
						INSERT INTO model_call (cache_key, purpose, model, response, input_tokens, output_tokens, images,
						                        cost_usd, success, created_at)
						VALUES (:key, :purpose, :model, :response, :in, :out, :images, :cost,
						        :success AND NOT EXISTS (SELECT 1 FROM model_call WHERE cache_key = :key AND success),
						        :at)
						""")
				.param("key", call.cacheKey())
				.param("purpose", call.purpose())
				.param("model", call.model())
				.param("response", call.response())
				.param("in", call.inputTokens())
				.param("out", call.outputTokens())
				.param("images", call.images())
				.param("cost", call.costUsd())
				.param("success", call.success())
				.param("at", Timestamp.from(clock.instant()))
				.update();
	}

	@Override
	public BigDecimal totalSpentUsd() {
		return jdbc.sql("SELECT coalesce(sum(cost_usd), 0) FROM model_call").query(BigDecimal.class).single();
	}
}
