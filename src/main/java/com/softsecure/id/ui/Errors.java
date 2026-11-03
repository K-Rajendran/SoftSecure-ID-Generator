package com.softsecure.id.ui;

import com.softsecure.id.service.UserException;
import java.awt.Component;
import java.util.UUID;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

public final class Errors {
    private static final Logger LOG = Logger.getLogger("com.softsecure.id");

    private Errors() { }

    public static void show(Component parent, Throwable failure) {
        String message;
        if (failure instanceof UserException) {
            message = failure.getMessage();
        } else {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            // JDBC exceptions and paths can contain personal data or credentials: never log their messages.
            String code = failure instanceof java.sql.SQLException sql ? " SQLState=" + sql.getSQLState() : "";
            LOG.warning("Operation failed: reference=" + reference + " type=" + failure.getClass().getSimpleName() + code);
            if (failure instanceof java.nio.file.FileAlreadyExistsException) {
                message = "The destination already exists. Choose a new filename.";
            } else {
                message = "The operation could not be completed. Check configuration and database availability.\n"
                        + "No success has been confirmed. Reference: " + reference;
            }
        }
        JOptionPane.showMessageDialog(parent, Theme.text(message), "SoftSecure · Action needed", JOptionPane.ERROR_MESSAGE);
    }
}
