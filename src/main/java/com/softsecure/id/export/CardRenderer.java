package com.softsecure.id.export;

import com.softsecure.id.config.AppConfig;
import com.softsecure.id.domain.IdRequest;
import com.softsecure.id.domain.Profile;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Renders a detached image, not a live Swing component; safe on a worker thread. */
public final class CardRenderer {
    private final QrService qr;
    private final String institution;
    private final String campus;

    public CardRenderer(QrService qr, AppConfig config) {
        this.qr = qr;
        institution = config.value("institution.name", "SoftSecure");
        campus = config.value("institution.campus", "Identity Services");
    }

    public BufferedImage render(IdRequest request) throws Exception {
        Profile profile = request.profile();
        BufferedImage image = new BufferedImage(1080, 740, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 1080, 740);
            graphics.setColor(new Color(20, 43, 69));
            graphics.fillRect(0, 0, 1080, 132);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 35));
            fit(graphics, institution, 36, 54, 990);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            fit(graphics, campus + "  /  " + request.role() + " IDENTITY CARD", 36, 96, 990);
            if (profile.photo().length > 0) {
                BufferedImage portrait = ImageIO.read(new ByteArrayInputStream(profile.photo()));
                if (portrait != null) {
                    double scale = Math.min(200.0 / portrait.getWidth(), 240.0 / portrait.getHeight());
                    int width = (int) (portrait.getWidth() * scale);
                    int height = (int) (portrait.getHeight() * scale);
                    graphics.drawImage(portrait, 36 + (200 - width) / 2, 164, width, height, null);
                    portrait.flush();
                }
            }
            graphics.setColor(new Color(20, 43, 69));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            fit(graphics, profile.name(), 270, 198, 750);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            fit(graphics, profile.identifier(), 270, 241, 750);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            fit(graphics, profile.department(), 270, 282, 750);
            fit(graphics, "Blood group: " + profile.bloodGroup(), 270, 327, 510);
            fit(graphics, "Phone: " + profile.phone(), 270, 368, 510);
            fit(graphics, "Emergency: " + profile.alternatePhone(), 270, 409, 510);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            fit(graphics, "Address", 36, 475, 740);
            wrap(graphics, profile.address().replace('\n', ' '), 36, 507, 730, 4);
            if (!request.token().isEmpty()) {
                graphics.drawImage(qr.generate(request.token()), 810, 350, null);
            }
            graphics.setColor(new Color(238, 243, 248));
            graphics.fillRect(0, 661, 1080, 79);
            graphics.setColor(new Color(20, 43, 69));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            graphics.drawString("Issued: " + profile.issueDate() + "   |   " + request.status(), 36, 694);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.drawString("Verify current approval in SoftSecure. A copied QR code does not prove the holder's identity.", 36, 722);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private static void fit(Graphics2D graphics, String text, int x, int y, int width) {
        String display = text;
        while (display.length() > 1 && graphics.getFontMetrics().stringWidth(display) > width) {
            display = display.substring(0, display.length() - 1);
        }
        if (!display.equals(text) && display.length() > 3) {
            display = display.substring(0, display.length() - 3) + "...";
        }
        graphics.drawString(display, x, y);
    }

    private static void wrap(Graphics2D graphics, String text, int x, int y, int width, int lines) {
        String remaining = text;
        for (int line = 0; line < lines && !remaining.isEmpty(); line++) {
            if (line == lines - 1) {
                fit(graphics, remaining, x, y + line * 29, width);
                break;
            }
            int end = remaining.length();
            while (end > 1 && graphics.getFontMetrics().stringWidth(remaining.substring(0, end)) > width) {
                end--;
            }
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) { end = space; }
            }
            graphics.drawString(remaining.substring(0, end), x, y + line * 29);
            remaining = remaining.substring(end).stripLeading();
        }
    }
}
