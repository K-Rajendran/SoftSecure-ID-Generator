package com.softsecure.id.ui;

import com.softsecure.id.security.Session;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

public final class DirectoryPanel extends JPanel {
    public DirectoryPanel(AppFrame frame) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page("Student card directory", "Look up an approved student card by exact registration number. Access is recorded in the audit log.");
        JPanel form = Theme.form();
        JTextField identifier = new JTextField(30);
        Theme.row(form, 0, "Registration number", identifier);
        Theme.row(form, 1, "", Theme.button("Find student", () -> {
            String id = identifier.getText();
            frame.work("Finding student…", () -> frame.services().ids().lookupStudent(session, id),
                    request -> CardPanel.open(frame, request, true));
        }));
        JPanel body = new JPanel(new BorderLayout());
        body.add(form, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }
}
