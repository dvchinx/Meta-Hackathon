package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ReportDamageUseCase;
import com.civicrepairs.application.port.out.GeoQueryPort;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
class ReportDamageService implements ReportDamageUseCase {

	private final ReportRepositoryPort reports;
	private final MediaStoragePort media;
	private final GeoQueryPort geo;
	private final ReportSettings settings;

	ReportDamageService(ReportRepositoryPort reports, MediaStoragePort media, GeoQueryPort geo,
			ReportSettings settings) {
		this.reports = reports;
		this.media = media;
		this.geo = geo;
		this.settings = settings;
	}

	@Override
	@Transactional
	public SubmitResult submit(Submission s) {
		Report report = reports.findOpenByReporter(s.reporterId(), s.channel()).orElse(null);
		if (report != null && s.channel() == ReportChannel.WEB) {
			// The web form sends complete reports: a new submission replaces an abandoned one.
			report.cancel();
			reports.save(report);
			report = null;
		}
		if (report == null) {
			if (reports.countCreatedSince(s.reporterId(), s.at().minus(Duration.ofDays(1)))
					>= settings.reportsPerReporterPerDay()) {
				return new SubmitResult(null, null, Set.of(), false, true, false);
			}
			report = Report.start(UUID.randomUUID(), s.channel(), s.reporterId(), s.at());
		}
		if (report.status() != ReportStatus.COLLECTING) {
			// Analyzing or waiting for a yes/no: nothing to add until that finishes.
			return result(report, false);
		}

		if (s.photo() != null) {
			var stored = media.store(s.photo().data(), s.photo().contentType(), MediaKind.PHOTO, Visibility.PRIVATE,
					null);
			report.attachPhoto(stored.id());
		}
		report.addContext(s.text());
		// The domain's box is a cheap first filter; the locality boundaries leave out Soacha, Chía, Funza...
		boolean outside = s.location() != null
				&& (geo.findLocality(s.location()).isEmpty() || !report.setLocation(s.location()));

		reports.save(report);
		return result(report, outside);
	}

	private static SubmitResult result(Report report, boolean locationOutsideBogota) {
		return new SubmitResult(report.id(), report.status(), report.missing(), locationOutsideBogota, false,
				report.isReadyForAnalysis());
	}
}
