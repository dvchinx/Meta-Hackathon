package com.civicrepairs.support;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Empties every table the tests write to, children first. Agencies, rules and localities are reference data. */
public final class Database {

	private static final String[] TABLES = {
			"notification_outbox", "pending_verification", "issue_follower", "verification", "agency_response",
			"petition", "filer_profile", "issue_endorsement", "report", "status_transition", "issue", "model_call",
			"media", "processed_message", "email_token", "user_session", "user_account"};

	private Database() {
	}

	public static void clean(JdbcClient jdbc) {
		for (String table : TABLES) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}
}
