package com.civicrepairs.adapters.in.whatsapp;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A fixed set of single-thread executors; a sender always lands on the same one. */
@Component
class StripedMessageDispatcher implements MessageDispatcher {

	private static final Logger log = LoggerFactory.getLogger(StripedMessageDispatcher.class);
	private static final int STRIPES = 4;

	private final ExecutorService[] stripes = new ExecutorService[STRIPES];

	StripedMessageDispatcher() {
		for (int i = 0; i < STRIPES; i++) {
			stripes[i] = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("whatsapp-" + i).factory());
		}
	}

	@Override
	public void dispatch(String key, Runnable task) {
		stripes[Math.floorMod(key.hashCode(), STRIPES)].execute(() -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				log.error("WhatsApp message handling failed", e);
			}
		});
	}

	@PreDestroy
	void shutdown() throws InterruptedException {
		for (ExecutorService stripe : stripes) {
			stripe.shutdown();
		}
		for (ExecutorService stripe : stripes) {
			stripe.awaitTermination(20, TimeUnit.SECONDS);
		}
	}
}
