package com.civicrepairs.application.service;

/**
 * @param reportsPerReporterPerDay  abuse limit per phone number (or web session)
 * @param dedupRadiusMeters         candidates for the same Issue must be this close
 * @param sameDamageMinConfidence   Muse Spark must be at least this sure that two photos show the same damage
 */
public record ReportSettings(int reportsPerReporterPerDay, double dedupRadiusMeters, double sameDamageMinConfidence) {
}
