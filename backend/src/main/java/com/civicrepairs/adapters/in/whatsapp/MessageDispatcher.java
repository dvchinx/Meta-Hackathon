package com.civicrepairs.adapters.in.whatsapp;

/**
 * Runs message handling off the webhook thread (Meta expects a fast 200). Tasks with the same key (sender) run one
 * at a time and in order; different senders run in parallel.
 */
public interface MessageDispatcher {

	void dispatch(String key, Runnable task);
}
