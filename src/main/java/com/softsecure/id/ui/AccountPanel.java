package com.softsecure.id.ui;

import com.softsecure.id.domain.Role;
import com.softsecure.id.security.Session;
import com.softsecure.id.service.UserException;
import java.awt.BorderLayout;
import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public final class AccountPanel extends JPanel {
    public AccountPanel(AppFrame frame, boolean create) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page(create ? "Create member account" : "Change password",
                create ? "Assign a unique user ID and share the initial password through a secure channel."
                        : "Use a unique passphrase of 12–128 characters. You will sign in again after saving.");
        JPanel form = Theme.form();
        JTextField username = new JTextField(28);
        JComboBox<Role> role = new JComboBox<>(new Role[] {Role.STUDENT, Role.FACULTY});
        JPasswordField current = new JPasswordField(28);
        JPasswordField password = new JPasswordField(28);
        JPasswordField confirmation = new JPasswordField(28);
        int row = 0;
        if (create) {
            Theme.row(form, row++, "User ID / registration number", username);
            Theme.row(form, row++, "Role", role);
        } else {
            Theme.row(form, row++, "Current password", current);
        }
        Theme.row(form, row++, "New password", password);
        Theme.row(form, row++, "Confirm password", confirmation);
        Theme.row(form, row, "", Theme.button(create ? "Create account" : "Save password", () -> {
            char[] secret = password.getPassword();
            char[] confirm = confirmation.getPassword();
            char[] old = current.getPassword();
            String name = username.getText();
            Role selected = (Role) role.getSelectedItem();
            password.setText("");
            confirmation.setText("");
            current.setText("");
            if (!Arrays.equals(secret, confirm)) {
                Arrays.fill(secret, '\0');
                Arrays.fill(confirm, '\0');
                Arrays.fill(old, '\0');
                Errors.show(frame, new UserException("The new passwords do not match."));
                return;
            }
            Arrays.fill(confirm, '\0');
            boolean scheduled = frame.work("Saving account…", () -> {
                try {
                    if (create) { frame.services().auth().createMember(session, name, secret, selected); }
                    else { frame.services().auth().changePassword(session, old, secret); }
                    return null;
                } finally {
                    Arrays.fill(old, '\0');
                    Arrays.fill(secret, '\0');
                }
            }, ignored -> {
                if (create) {
                    frame.dashboard();
                    frame.notifySuccess("Account created. The member can now sign in and submit details.");
                } else {
                    frame.logout();
                    frame.notifySuccess("Password changed. Sign in with the new password.");
                }
            });
            if (!scheduled) {
                Arrays.fill(old, '\0');
                Arrays.fill(secret, '\0');
            }
        }));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }
}
