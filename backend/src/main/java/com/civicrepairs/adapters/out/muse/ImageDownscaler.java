package com.civicrepairs.adapters.out.muse;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Image tokens scale with resolution: never send more than {@code maxSide} pixels to a model. */
final class ImageDownscaler {

	record Jpeg(byte[] data, String contentType) {
	}

	private ImageDownscaler() {
	}

	/** Returns a JPEG no larger than maxSide; unreadable formats are passed through unchanged. */
	static Jpeg toJpeg(byte[] original, String contentType, int maxSide) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
			if (image == null) {
				return new Jpeg(original, contentType);
			}
			double scale = Math.min(1.0, (double) maxSide / Math.max(image.getWidth(), image.getHeight()));
			int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
			int height = Math.max(1, (int) Math.round(image.getHeight() * scale));

			BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = rgb.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, 0, 0, width, height, null);
			g.dispose();
			return new Jpeg(encode(rgb, 0.85f), "image/jpeg");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static byte[] encode(BufferedImage image, float quality) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		var out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam params = writer.getDefaultWriteParam();
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(quality);
			writer.write(null, new IIOImage(image, null, null), params);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}
}
