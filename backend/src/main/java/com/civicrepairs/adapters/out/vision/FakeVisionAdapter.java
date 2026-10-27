package com.civicrepairs.adapters.out.vision;

import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.VisionPort;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Offline stand-in for vision-service (no VISION_SERVICE_URL). Returns gray placeholders, never the original
 * photo: a misconfiguration must not be able to publish an unblurred image.
 */
@Component
@ConditionalOnExpression("'${civic.vision.url:}'.isBlank()")
class FakeVisionAdapter implements VisionPort {

	private static final byte[] PLACEHOLDER = placeholder(new Color(0x9e9e9e));
	private static final byte[] MARKED = placeholder(new Color(0xd62828));

	@Override
	public VisionResult analyze(MediaContent photo, DamageType damageType) {
		return new VisionResult(damageType == DamageType.OTHER ? 0 : 0.1, damageType == DamageType.OTHER ? 0 : 1, 0,
				0, PLACEHOLDER.clone(), MARKED.clone());
	}

	private static byte[] placeholder(Color color) {
		var image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, 320, 240);
		g.dispose();
		var out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "jpeg", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}
}
