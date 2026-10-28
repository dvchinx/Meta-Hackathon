package com.civicrepairs.config;

import com.civicrepairs.application.service.ReportSettings;
import com.civicrepairs.application.service.ReporterIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MetaApiProperties.class, WhatsAppProperties.class})
class ApplicationConfig {

	private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);
	static final String DEV_SALT = "dev-only-salt-not-for-production";

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ReportSettings reportSettings(@Value("${civic.reports.per-reporter-per-day}") int perDay,
			@Value("${civic.reports.dedup-radius-meters}") double radius,
			@Value("${civic.reports.same-damage-min-confidence}") double sameDamage) {
		return new ReportSettings(perDay, radius, sameDamage);
	}

	@Bean
	ReporterIds reporterIds(@Value("${civic.reporter-hash-salt:}") String salt) {
		if (salt.isBlank() || DEV_SALT.equals(salt)) {
			log.warn("PHONE_HASH_SALT is not set: using the development salt. Never do this in production.");
			return new ReporterIds(DEV_SALT);
		}
		return new ReporterIds(salt);
	}
}
