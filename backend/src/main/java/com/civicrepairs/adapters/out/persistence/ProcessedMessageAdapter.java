package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.ProcessedMessagePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;

@Component
class ProcessedMessageAdapter implements ProcessedMessagePort {

	private final JdbcClient jdbc;
	private final Clock clock;

	ProcessedMessageAdapter(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public boolean markIfNew(String channel, String messageId) {
		return jdbc.sql("""
						INSERT INTO processed_message (channel, message_id, processed_at) VALUES (:channel, :id, :at)
						ON CONFLICT DO NOTHING
						""")
				.param("channel", channel)
				.param("id", messageId)
				.param("at", Timestamp.from(clock.instant()))
				.update() == 1;
	}
}
