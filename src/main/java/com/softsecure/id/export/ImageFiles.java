package com.softsecure.id.export;

import com.softsecure.id.service.UserException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ImageFiles {
    private ImageFiles() { }

    public static BufferedImage read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 10_000_000) {
            throw new UserException("Choose a PNG or JPEG file smaller than 10 MB.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new UserException("The image could not be opened.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UserException("Choose a valid PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) {
                    throw new UserException("Only PNG and JPEG images are supported.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > 16_000_000) {
                    throw new UserException("Image dimensions must be at most 6000 pixels and 16 megapixels.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    public static byte[] portrait(Path path) throws IOException {
        BufferedImage source = read(path);
        double scale = Math.min(1, 600.0 / Math.max(source.getWidth(), source.getHeight()));
        BufferedImage normalized = new BufferedImage(Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, normalized.getWidth(), normalized.getHeight(), null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(normalized, "png", output);
            return output.toByteArray();
        } finally {
            normalized.flush();
        }
    }
}
