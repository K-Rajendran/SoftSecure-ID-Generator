package com.softsecure.id.ui;

import com.softsecure.id.domain.IdRequest;
import com.softsecure.id.domain.Profile;
import com.softsecure.id.domain.Role;
import com.softsecure.id.export.ImageFiles;
import com.softsecure.id.security.Session;
import com.softsecure.id.service.UserException;
import com.softsecure.id.service.Validation;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class ProfilePanel extends JPanel {
    private byte[] portrait = new byte[0];

    public ProfilePanel(AppFrame frame, IdRequest previous) {
        super(new BorderLayout());
        Session session = frame.session();
        JPanel page = Theme.page(previous == null ? "Submit identity details" : "Correct your request",
                "All fields are required. Dates use YYYY-MM-DD. Submissions need administrative approval before export.");
        JPanel form = Theme.form();
        JTextField identifier = new JTextField(32);
        identifier.setText(session.role() == Role.ADMIN ? "" : session.username());
        identifier.setEditable(session.role() == Role.ADMIN && previous == null);
        JTextField name = new JTextField(32);
        JComboBox<String> department = new JComboBox<>(new String[] {"DAYSCHOLAR", "HOSTELLER", "SAS", "SCORE",
                "V-SIGN", "SSL", "SENSE", "SCE", "SBST", "VAIAL", "VIT BS", "SCOPE", "V-SPARC", "SMEC", "SELECT", "SCHEME", "HOT"});
        department.setEditable(true);
        JComboBox<String> gender = new JComboBox<>(Validation.GENDERS);
        gender.setSelectedIndex(-1);
        JComboBox<String> blood = new JComboBox<>(Validation.BLOOD_GROUPS);
        blood.setSelectedIndex(-1);
        JTextField birthDate = new JTextField(32);
        JTextField issueDate = new JTextField(LocalDate.now().toString(), 32);
        JTextField phone = new JTextField(32);
        JTextField alternate = new JTextField(32);
        JTextField email = new JTextField(32);
        JTextArea address = new JTextArea(4, 32);
        address.setLineWrap(true);
        address.setWrapStyleWord(true);
        address.getAccessibleContext().setAccessibleName("Address");
        JLabel photoStatus = Theme.label("No portrait selected");
        if (previous != null) {
            Profile profile = previous.profile();
            identifier.setText(profile.identifier());
            name.setText(profile.name());
            department.setSelectedItem(profile.department());
            gender.setSelectedItem(profile.gender());
            blood.setSelectedItem(profile.bloodGroup());
            birthDate.setText(profile.birthDate().toString());
            issueDate.setText(profile.issueDate().toString());
            phone.setText(profile.phone());
            alternate.setText(profile.alternatePhone());
            email.setText(profile.email());
            address.setText(profile.address());
            portrait = profile.photo();
            photoStatus.setText(portrait.length == 0 ? "Choose a portrait to complete this imported record" : "Existing portrait retained");
        }
        int row = 0;
        Theme.row(form, row++, "User ID / registration number", identifier);
        Theme.row(form, row++, "Full name", name);
        Theme.row(form, row++, "Study mode / department", department);
        Theme.row(form, row++, "Gender", gender);
        Theme.row(form, row++, "Blood group", blood);
        Theme.row(form, row++, "Birth date (YYYY-MM-DD)", birthDate);
        Theme.row(form, row++, "Issue date (YYYY-MM-DD)", issueDate);
        Theme.row(form, row++, "Phone", phone);
        Theme.row(form, row++, "Parent / emergency phone", alternate);
        Theme.row(form, row++, "Email", email);
        JScrollPane addressScroll = new JScrollPane(address);
        addressScroll.getAccessibleContext().setAccessibleName("Address");
        Theme.row(form, row++, "Address", addressScroll);
        JPanel photo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        photo.setOpaque(false);
        photo.add(Theme.button("Choose portrait…", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Portrait image (PNG or JPEG)", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path path = chooser.getSelectedFile().toPath();
                frame.work("Preparing portrait…", () -> ImageFiles.portrait(path), bytes -> {
                    portrait = bytes;
                    photoStatus.setText("Portrait ready · " + (bytes.length / 1024) + " KB");
                });
            }
        }));
        photo.add(photoStatus);
        Theme.row(form, row, "Portrait", photo);
        JPanel formTop = new JPanel(new BorderLayout());
        formTop.setOpaque(false);
        formTop.add(form, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(formTop);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        page.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(Theme.button("Cancel", frame::dashboard));
        footer.add(Theme.button("Submit for review", () -> {
            try {
                Profile profile = new Profile(Validation.identifier(identifier.getText()), name.getText().strip(),
                        String.valueOf(department.getEditor().getItem()).strip(), (String) gender.getSelectedItem(),
                        (String) blood.getSelectedItem(), address.getText().strip(), phone.getText().strip(),
                        alternate.getText().strip(), email.getText().strip(), LocalDate.parse(birthDate.getText().strip()),
                        LocalDate.parse(issueDate.getText().strip()), portrait);
                Validation.profile(profile);
                frame.work("Submitting for review…", () -> {
                    frame.services().ids().submit(session, profile, previous == null ? 0 : previous.version());
                    return null;
                }, ignored -> {
                    frame.dashboard();
                    frame.notifySuccess("Your details were saved and are pending review. Cards are available after approval.");
                });
            } catch (DateTimeParseException exception) {
                Errors.show(frame, new UserException("Enter valid dates using YYYY-MM-DD, for example 2001-06-15."));
            } catch (UserException exception) {
                Errors.show(frame, exception);
            }
        }));
        page.add(footer, BorderLayout.SOUTH);
        add(page);
    }
}
