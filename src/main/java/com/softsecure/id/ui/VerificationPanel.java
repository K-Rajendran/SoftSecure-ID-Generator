package com.softsecure.id.ui;

import com.softsecure.id.security.Session;
import java.awt.BorderLayout;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class VerificationPanel extends JPanel {
    public VerificationPanel(AppFrame frame) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page("Verify identity card", "Verification checks current database approval. Compare the returned identity and card portrait with the holder.");
        JPanel form = Theme.form();
        JTextField payload = new JTextField(48);
        Theme.row(form, 0, "QR text (SSID1.…)", payload);
        Theme.row(form, 1, "", Theme.button("Verify text", () -> {
            String text = payload.getText().strip();
            frame.work("Checking current approval…", () -> frame.services().ids().verify(session, text), frame::notifySuccess);
        }));
        Theme.row(form, 2, "", Theme.button("Read QR from image…", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("QR image (PNG or JPEG)", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path path = chooser.getSelectedFile().toPath();
                frame.work("Reading and verifying QR…", () -> frame.services().ids().verify(session,
                        frame.services().qr().decode(path)), frame::notifySuccess);
            }
        }));
        Theme.row(form, 3, "", Theme.text("Legacy QR codes contain personal data and have no verification authority.\n"
                + "Reissue legacy cards after import and review. Verification requires an active session and a database connection."));
        JPanel body = new JPanel(new BorderLayout());
        body.add(form, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }
}
