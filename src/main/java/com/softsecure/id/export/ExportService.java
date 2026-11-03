package com.softsecure.id.export;

import com.softsecure.id.security.Session;
import com.softsecure.id.service.IdService;
import com.softsecure.id.service.Validation;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

public final class ExportService {
    private final IdService ids;
    private final CardRenderer renderer;

    public ExportService(IdService ids, CardRenderer renderer) {
        this.ids = ids;
        this.renderer = renderer;
    }

    public BufferedImage image(Session session, String requestId) throws Exception {
        return renderer.render(ids.approvedForExport(session, requestId));
    }

    public void export(Session session, String requestId, Path destination, char[] pdfPassword) throws Exception {
        Path staging = null;
        try {
            if (pdfPassword != null) {
                Validation.password(pdfPassword);
            }
            Path target = destination.toAbsolutePath().normalize();
            BufferedImage image = image(session, requestId);
            // Staging never contains plaintext for PDF; PNG export is explicitly unencrypted.
            try {
                staging = Files.createTempFile(target.getParent(), ".softsecure-", ".part");
                try (OutputStream output = Files.newOutputStream(staging, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    if (pdfPassword == null) {
                        if (!ImageIO.write(image, "png", output)) {
                            throw new java.io.IOException("PNG encoder unavailable");
                        }
                    } else {
                        writePdf(image, output, pdfPassword);
                    }
                }
            } finally {
                image.flush();
            }
            session.requireActive();
            // No REPLACE_EXISTING: a raced or existing destination is never overwritten.
            Files.move(staging, target);
            staging = null;
        } finally {
            if (pdfPassword != null) { Arrays.fill(pdfPassword, '\0'); }
            if (staging != null) { Files.deleteIfExists(staging); }
        }
    }

    private void writePdf(BufferedImage image, OutputStream output, char[] password) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(540, 370));
            document.addPage(page);
            AccessPermission permissions = new AccessPermission();
            permissions.setCanModify(false);
            permissions.setCanExtractContent(false);
            permissions.setCanModifyAnnotations(false);
            permissions.setCanFillInForm(false);
            permissions.setCanAssembleDocument(false);
            permissions.setCanPrint(true);
            StandardProtectionPolicy protection = new StandardProtectionPolicy(UUID.randomUUID().toString(),
                    new String(password), permissions);
            protection.setEncryptionKeyLength(256);
            protection.setPreferAES(true);
            document.protect(protection);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, 540, 370);
            }
            document.save(output);
        }
    }
}
