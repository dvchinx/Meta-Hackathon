package com.civicrepairs.adapters.out.storage;

import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Files under {@code <root>/private} and {@code <root>/public}; metadata in the {@code media} table.
 * Originals never leave the private directory.
 */
@Component
class FileSystemMediaStorageAdapter implements MediaStoragePort {

	private final Path root;
	private final JdbcClient jdbc;
	private final Clock clock;

	FileSystemMediaStorageAdapter(@Value("${civic.media.path}") Path root, JdbcClient jdbc, Clock clock) {
		this.root = root.toAbsolutePath().normalize();
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Media store(byte[] data, String contentType, MediaKind kind, Visibility visibility, UUID derivedFrom) {
		var media = new Media(UUID.randomUUID(), kind, visibility, contentType, sha256(data), data.length, derivedFrom);
		Path target = pathOf(media.id(), visibility);
		try {
			Files.createDirectories(target.getParent());
			Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
			Files.write(tmp, data);
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			throw new UncheckedIOException("could not store media " + media.id(), e);
		}
		jdbc.sql("""
						INSERT INTO media (id, kind, visibility, content_type, sha256, size_bytes, derived_from, created_at)
						VALUES (:id, :kind, :visibility, :type, :sha, :size, :derived, :at)
						""")
				.param("id", media.id())
				.param("kind", kind.name())
				.param("visibility", visibility.name())
				.param("type", contentType)
				.param("sha", media.sha256())
				.param("size", media.sizeBytes())
				.param("derived", derivedFrom)
				.param("at", Timestamp.from(clock.instant()))
				.update();
		return media;
	}

	@Override
	public MediaContent load(UUID id) {
		Media media = find(id).orElseThrow(() -> new IllegalArgumentException("unknown media " + id));
		return new MediaContent(media, read(media));
	}

	@Override
	public Optional<MediaContent> loadPublic(UUID id) {
		return find(id)
				.filter(media -> media.visibility() == Visibility.PUBLIC)
				.map(media -> new MediaContent(media, read(media)));
	}

	private Optional<Media> find(UUID id) {
		return jdbc.sql("SELECT * FROM media WHERE id = :id").param("id", id).query(this::map).optional();
	}

	private byte[] read(Media media) {
		try {
			return Files.readAllBytes(pathOf(media.id(), media.visibility()));
		} catch (IOException e) {
			throw new UncheckedIOException("could not read media " + media.id(), e);
		}
	}

	private Path pathOf(UUID id, Visibility visibility) {
		return root.resolve(visibility.name().toLowerCase()).resolve(id.toString());
	}

	private Media map(ResultSet rs, int row) throws SQLException {
		return new Media(rs.getObject("id", UUID.class), MediaKind.valueOf(rs.getString("kind")),
				Visibility.valueOf(rs.getString("visibility")), rs.getString("content_type"), rs.getString("sha256"),
				rs.getLong("size_bytes"), rs.getObject("derived_from", UUID.class));
	}

	static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
