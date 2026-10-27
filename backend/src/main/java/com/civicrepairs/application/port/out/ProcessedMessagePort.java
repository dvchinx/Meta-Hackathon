package com.civicrepairs.application.port.out;

/** Idempotency for chat webhooks, which redeliver messages. Stores message ids only, never content or numbers. */
public interface ProcessedMessagePort {

	/** @return true the first time a message id is seen, false for every redelivery */
	boolean markIfNew(String channel, String messageId);
}
