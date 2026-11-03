package com.softsecure.id.export;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

public final class QrService {
    public BufferedImage generate(String token) throws Exception {
        return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode("SSID1." + token,
                BarcodeFormat.QR_CODE, 240, 240,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4)));
    }

    public String decode(Path path) throws Exception {
        BufferedImage image = ImageFiles.read(path);
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        } catch (com.google.zxing.NotFoundException exception) {
            throw new com.softsecure.id.service.UserException("No readable QR code was found. Choose a clear QR image.");
        } finally {
            image.flush();
        }
    }
}
