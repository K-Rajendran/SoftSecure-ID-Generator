package com.softsecure.id.ui;

import com.softsecure.id.domain.IdRequest;
import com.softsecure.id.domain.Profile;
import com.softsecure.id.domain.Role;
import com.softsecure.id.domain.Status;
import com.softsecure.id.security.Session;
import com.softsecure.id.service.UserException;
import com.softsecure.id.service.Validation;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

public final class CardPanel extends JPanel {
    public static void open(AppFrame frame, IdRequest request, boolean directory) {
        frame.work("Preparing card preview…", () -> frame.services().renderer().render(request),
                image -> frame.showPage(new CardPanel(frame, request, image, directory)));
    }

    private CardPanel(AppFrame frame, IdRequest request, BufferedImage image, boolean directory) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page(request.status() == Status.APPROVED ? "Approved identity card" : "Identity request · " + request.status(),
                "Reference " + request.id() + " · Version " + request.version()
                        + (request.reason().isBlank() ? "" : " · " + request.reason()));
        JPanel preview = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                double scale = Math.min((getWidth() - 24.0) / image.getWidth(), (getHeight() - 24.0) / image.getHeight());
                if (scale > 0) {
                    int width = (int) (image.getWidth() * scale);
                    int height = (int) (image.getHeight() * scale);
                    graphics.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
                }
            }
        };
        preview.setPreferredSize(new java.awt.Dimension(760, 520));
        preview.getAccessibleContext().setAccessibleName("Identity card preview; equivalent details follow");
        Profile profile = request.profile();
        String details = "Name: " + profile.name() + "\nID: " + profile.identifier() + "\nDepartment / study mode: " + profile.department()
                + "\nBlood group: " + profile.bloodGroup() + "\nPhone: " + profile.phone()
                + "\nParent / emergency phone: " + profile.alternatePhone() + "\nAddress: " + profile.address()
                + "\nIssue date: " + profile.issueDate();
        if (!directory) {
            details += "\nEmail: " + profile.email() + "\nGender: " + profile.gender() + "\nBirth date: " + profile.birthDate();
        }
        JTextArea accessible = Theme.text(details);
        accessible.getAccessibleContext().setAccessibleName("Complete identity details");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, preview, new JScrollPane(accessible));
        split.setResizeWeight(0.8);
        split.setBorder(null);
        page.add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new java.awt.GridLayout(0, 3, 8, 8));
        actions.add(Theme.button("Dashboard", frame::dashboard));
        if (!directory && request.status() == Status.REJECTED) {
            actions.add(Theme.button("Correct and resubmit", () -> frame.showPage(new ProfilePanel(frame, request))));
        }
        if (!directory && session.role() == Role.ADMIN) {
            if (request.status() == Status.PENDING) {
                actions.add(Theme.button("Reject", () -> review(frame, session, request, false)));
                actions.add(Theme.button("Approve", () -> review(frame, session, request, true)));
            } else if (request.status() == Status.APPROVED) {
                actions.add(Theme.button("Revoke", () -> review(frame, session, request, false)));
            }
        }
        if (!directory && request.status() == Status.APPROVED) {
            actions.add(Theme.button("Export PNG", () -> export(frame, session, request, false)));
            actions.add(Theme.button("Protected PDF", () -> export(frame, session, request, true)));
            actions.add(Theme.button("Print", () -> print(frame, session, request)));
        }
        page.add(actions, BorderLayout.SOUTH);
        add(page);
    }

    private static void review(AppFrame frame, Session session, IdRequest request, boolean approve) {
        String reason = "";
        if (approve) {
            if (JOptionPane.showConfirmDialog(frame, "Approve this request and enable card export?", "Approve request",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { return; }
        } else {
            JTextArea input = new JTextArea(5, 38);
            input.setLineWrap(true);
            input.setWrapStyleWord(true);
            input.getAccessibleContext().setAccessibleName("Reason for rejection or revocation");
            if (JOptionPane.showConfirmDialog(frame, new JScrollPane(input), "Reason for rejection / revocation",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { return; }
            reason = input.getText();
        }
        String explanation = reason;
        frame.work("Saving review decision…", () -> {
            frame.services().ids().review(session, request.id(), request.version(), approve, explanation);
            return null;
        }, ignored -> {
            frame.dashboard();
            frame.notifySuccess(approve ? "Request approved. The card is ready for export." : "Decision saved. Any previous QR reference is no longer valid.");
        });
    }

    private static void export(AppFrame frame, Session session, IdRequest request, boolean pdf) {
        char[] password = null;
        if (pdf) {
            JPasswordField input = new JPasswordField(28);
            JPasswordField confirm = new JPasswordField(28);
            JPanel form = Theme.form();
            Theme.row(form, 0, "PDF password (12–128 characters)", input);
            Theme.row(form, 1, "Confirm PDF password", confirm);
            int choice = JOptionPane.showConfirmDialog(frame, form, "Choose a separate PDF password", JOptionPane.OK_CANCEL_OPTION);
            char[] candidate = input.getPassword();
            char[] repeated = confirm.getPassword();
            input.setText("");
            confirm.setText("");
            boolean accepted = false;
            try {
                if (choice != JOptionPane.OK_OPTION) { return; }
                Validation.password(candidate);
                if (!Arrays.equals(candidate, repeated)) { throw new UserException("The PDF passwords do not match."); }
                password = candidate;
                accepted = true;
            } catch (UserException exception) {
                Errors.show(frame, exception);
                return;
            } finally {
                Arrays.fill(repeated, '\0');
                if (!accepted) { Arrays.fill(candidate, '\0'); }
            }
        } else if (JOptionPane.showConfirmDialog(frame, "PNG files contain personal details and are not encrypted. Continue?",
                "Export image", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("identity-" + request.id() + (pdf ? ".pdf" : ".png")));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            if (password != null) { Arrays.fill(password, '\0'); }
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        String extension = pdf ? ".pdf" : ".png";
        if (!destination.toString().toLowerCase(java.util.Locale.ROOT).endsWith(extension)) {
            destination = Path.of(destination + extension);
        }
        Path target = destination;
        char[] pdfPassword = password;
        boolean scheduled = frame.work("Exporting approved card…", () -> {
            frame.services().exports().export(session, request.id(), target, pdfPassword);
            return null;
        }, ignored -> frame.notifySuccess("Card exported to " + target.toAbsolutePath()));
        if (!scheduled && pdfPassword != null) { Arrays.fill(pdfPassword, '\0'); }
    }

    private static void print(AppFrame frame, Session session, IdRequest request) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("SoftSecure identity card");
        if (!job.printDialog()) { return; }
        frame.work("Printing approved card…", () -> {
            BufferedImage printable = frame.services().exports().image(session, request.id());
            try {
                job.setPrintable((graphics, format, index) -> {
                    if (index > 0) { return Printable.NO_SUCH_PAGE; }
                    Graphics2D copy = (Graphics2D) graphics.create();
                    try {
                        double scale = Math.min(format.getImageableWidth() / printable.getWidth(),
                                format.getImageableHeight() / printable.getHeight());
                        copy.translate(format.getImageableX(), format.getImageableY());
                        copy.scale(scale, scale);
                        copy.drawImage(printable, 0, 0, null);
                        return Printable.PAGE_EXISTS;
                    } finally { copy.dispose(); }
                });
                session.requireActive();
                job.print();
            } finally { printable.flush(); }
            return null;
        }, ignored -> frame.notifySuccess("The card was sent to the printer."));
    }
}
