package com.softsecure.id.ui;

import com.softsecure.id.domain.Role;
import com.softsecure.id.security.Session;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JPanel;

public final class DashboardPanel extends JPanel {
    public DashboardPanel(AppFrame frame) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page("Identity workspace", "Submit details, follow review progress and manage approved identity cards.");
        JPanel actions = new JPanel(new GridLayout(0, 2, 16, 16));
        actions.setOpaque(false);
        if (session.role() == Role.ADMIN) {
            actions.add(Theme.button("Create member account", () -> frame.showPage(new AccountPanel(frame, true))));
            actions.add(Theme.button("Submit member details", () -> frame.showPage(new ProfilePanel(frame, null))));
            actions.add(Theme.button("Review requests", () -> {
                ReviewPanel panel = new ReviewPanel(frame);
                frame.showPage(panel);
                panel.refresh();
            }));
        } else {
            actions.add(Theme.button("My request / ID card", () -> frame.work("Loading your request…",
                    () -> frame.services().ids().own(session), request -> {
                        if (request == null) {
                            frame.showPage(new ProfilePanel(frame, null));
                        } else {
                            CardPanel.open(frame, request, false);
                        }
                    })));
        }
        if (session.role() == Role.ADMIN || (session.role() == Role.FACULTY && frame.services().ids().facultyLookupEnabled())) {
            actions.add(Theme.button("Find approved student card", () -> frame.showPage(new DirectoryPanel(frame))));
        }
        actions.add(Theme.button("Verify a QR code", () -> frame.showPage(new VerificationPanel(frame))));
        JPanel body = new JPanel(new BorderLayout(0, 24));
        body.setOpaque(false);
        body.add(actions, BorderLayout.NORTH);
        body.add(Theme.text("Submission → Administrative review → Approval → Card and QR export\n\n"
                + "Rejected requests can be corrected and submitted again. Approved cards can be revoked by an administrator.\n"
                + "PDF exports use a separate password of your choice. PNG images and printed cards are not encrypted."), BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }
}
