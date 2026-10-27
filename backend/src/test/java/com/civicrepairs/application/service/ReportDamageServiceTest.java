package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Submission;
import com.civicrepairs.domain.report.MissingItem;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.GeoPoint;
import com.civicrepairs.support.InMemoryMediaStorage;
import com.civicrepairs.support.InMemoryReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDamageServiceTest {

	private static final Instant T0 = Instant.parse("2026-10-26T15:00:00Z");
	private static final GeoPoint CHAPINERO = new GeoPoint(4.6486, -74.0628);
	private static final Photo PHOTO = new Photo(new byte[] {1, 2, 3}, "image/jpeg");

	private final InMemoryReportRepository reports = new InMemoryReportRepository();
	private final ReportDamageService service =
			new ReportDamageService(reports, new InMemoryMediaStorage(), new ReportSettings(2, 30, 0.7));

	private static Submission whatsapp(Photo photo, String text, GeoPoint location, Instant at) {
		return new Submission(ReportChannel.WHATSAPP, "hash-1", photo, text, location, at);
	}

	@Test
	void collectsAcrossMessagesUntilReady() {
		var first = service.submit(whatsapp(null, "Hay un hueco enorme", null, T0));
		assertThat(first.missing()).containsExactlyInAnyOrder(MissingItem.PHOTO, MissingItem.LOCATION);

		var second = service.submit(whatsapp(PHOTO, null, null, T0));
		assertThat(second.reportId()).isEqualTo(first.reportId());
		assertThat(second.missing()).containsExactly(MissingItem.LOCATION);

		var third = service.submit(whatsapp(null, null, CHAPINERO, T0));
		assertThat(third.readyForAnalysis()).isTrue();
		assertThat(reports.findById(first.reportId()).orElseThrow().context()).contains("Hay un hueco enorme");
	}

	@Test
	void everythingInOneMessageIsReadyAtOnce() {
		var result = service.submit(whatsapp(PHOTO, "hueco", CHAPINERO, T0));

		assertThat(result.readyForAnalysis()).isTrue();
		assertThat(result.missing()).isEmpty();
	}

	@Test
	void flagsLocationsOutsideBogota() {
		var result = service.submit(whatsapp(PHOTO, null, new GeoPoint(6.2442, -75.5812), T0));

		assertThat(result.locationOutsideBogota()).isTrue();
		assertThat(result.missing()).containsExactly(MissingItem.LOCATION);
	}

	@Test
	void limitsNewReportsPerReporterPerDay() {
		for (int i = 0; i < 2; i++) {
			var r = service.submit(whatsapp(PHOTO, null, CHAPINERO, T0.plusSeconds(i)));
			reports.findById(r.reportId()).orElseThrow().cancel();
		}

		assertThat(service.submit(whatsapp(PHOTO, null, CHAPINERO, T0.plusSeconds(10))).rateLimited()).isTrue();
		assertThat(service.submit(whatsapp(PHOTO, null, CHAPINERO, T0.plus(25, ChronoUnit.HOURS))).rateLimited())
				.isFalse();
	}

	@Test
	void aNewWebSubmissionReplacesTheAbandonedOne() {
		var web = new Submission(ReportChannel.WEB, "web-hash", PHOTO, null, CHAPINERO, T0);
		var first = service.submit(web);
		var second = service.submit(web);

		assertThat(second.reportId()).isNotEqualTo(first.reportId());
		assertThat(reports.findById(first.reportId()).orElseThrow().status()).isEqualTo(ReportStatus.CANCELLED);
	}
}
