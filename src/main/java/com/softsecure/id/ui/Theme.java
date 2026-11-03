package com.softsecure.id.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

public final class Theme {
    public static final Color NAVY = new Color(20, 43, 69);
    public static final Color BLUE = new Color(28, 86, 150);
    public static final Color BACKGROUND = new Color(242, 246, 250);
    public static final Color MUTED = new Color(75, 89, 106);

    private Theme() { }

    public static void install() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
            // The platform look and feel remains a functional fallback.
        }
        UIManager.put("defaultFont", new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("control", BACKGROUND);
        UIManager.put("nimbusBase", NAVY);
        UIManager.put("nimbusSelectionBackground", BLUE);
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("TextField.margin", new Insets(6, 8, 6, 8));
    }

    public static JPanel page(String title, String subtitle) {
        JPanel page = new JPanel(new BorderLayout(20, 20));
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        page.setBackground(BACKGROUND);
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        JLabel heading = label(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26));
        heading.setForeground(NAVY);
        header.add(heading, BorderLayout.NORTH);
        JTextArea description = text(subtitle);
        description.setForeground(MUTED);
        header.add(description, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);
        return page;
    }

    /** Disables Swing's automatic HTML rendering for values that may contain user text. */
    public static JLabel label(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }

    public static JTextArea text(String value) {
        JTextArea area = new JTextArea(value);
        area.setColumns(48);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        return area;
    }

    public static JButton button(String title, Runnable action) {
        JButton button = new JButton(title);
        button.setMargin(new Insets(8, 16, 8, 16));
        button.getAccessibleContext().setAccessibleName(title);
        button.addActionListener(event -> action.run());
        return button;
    }

    public static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    public static void row(JPanel form, int row, String title, JComponent field) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(7, 0, 7, 18);
        JLabel label = label(title);
        label.setLabelFor(field);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(3, 0, 3, 0);
        if (!title.isBlank()) {
            field.getAccessibleContext().setAccessibleName(title);
        }
        form.add(field, constraints);
    }
}
