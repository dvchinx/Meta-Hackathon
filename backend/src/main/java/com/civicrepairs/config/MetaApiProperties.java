package com.civicrepairs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Meta Model API settings. With {@code enabled=false} (the default) every model adapter is a fake and no credit is
 * spent. Prices in USD per million tokens / per image, from docs/meta-api.md.
 */
@ConfigurationProperties("civic.meta")
public record MetaApiProperties(
		boolean enabled,
		String apiKey,
		String baseUrl,
		String museModel,
		BigDecimal budgetLimitUsd,
		BigDecimal museInputPerMillion,
		BigDecimal museCachedInputPerMillion,
		BigDecimal museOutputPerMillion) {
}
