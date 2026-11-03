package com.softsecure.id.ui;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class LoginPanel extends JPanel {
    public LoginPanel(AppFrame frame) {
        super(new BorderLayout());
        JPanel page = Theme.page("Welcome to SoftSecure", "Sign in with your assigned user ID. Your account determines your role.");
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        JPanel form = Theme.form();
        JTextField username = new JTextField(25);
        JPasswordField password = new JPasswordField(25);
        Theme.row(form, 0, "User ID", username);
        Theme.row(form, 1, "Password", password);
        JButton signIn = Theme.button("Sign in", () -> {
            String name = username.getText();
            char[] secret = password.getPassword();
            password.setText("");
            if (!frame.work("Signing in…", () -> frame.services().auth().login(name, secret), frame::signedIn)) {
                java.util.Arrays.fill(secret, '\0');
            }
        });
        signIn.setMnemonic('S');
        Theme.row(form, 2, "", signIn);
        Theme.row(form, 3, "", Theme.text("Need access? Contact your institution's administrator.\nFive failed attempts lock an account for 15 minutes."));
        center.add(form);
        page.add(center, BorderLayout.CENTER);
        add(page);
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                frame.getRootPane().setDefaultButton(signIn);
                username.requestFocusInWindow();
            }
        });
    }
}
