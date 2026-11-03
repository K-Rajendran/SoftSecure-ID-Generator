package com.softsecure.id.ui;

import com.softsecure.id.AppServices;
import com.softsecure.id.security.Session;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** One navigation shell. Every asynchronous result is bound to a screen/session generation. */
public final class AppFrame extends JFrame {
    private final AppServices services;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel(new BorderLayout(0, 6));
    private final JLabel status = Theme.label("Ready");
    private final JPanel busyPane = new JPanel(new java.awt.GridBagLayout());
    private final JLabel busyLabel = Theme.label("Working…");
    private final Timer expiryTimer;
    private final AWTEventListener activityListener;
    private final java.awt.KeyEventDispatcher busyKeys;
    private Session session;
    private boolean busy;
    private long generation;

    public AppFrame(AppServices services) {
        super("SoftSecure · Identity Management");
        this.services = services;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 620));
        setSize(1120, 850);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        navigation.setBackground(Theme.NAVY);
        navigation.setBorder(BorderFactory.createEmptyBorder(10, 18, 8, 18));
        add(navigation, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        add(status, BorderLayout.SOUTH);
        busyPane.setBackground(new java.awt.Color(242, 246, 250, 235));
        busyPane.add(busyLabel);
        busyPane.setFocusTraversalKeysEnabled(false);
        busyPane.addMouseListener(new java.awt.event.MouseAdapter() { });
        busyPane.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
        setGlassPane(busyPane);
        busyKeys = event -> busy && event.getSource() instanceof Component component
                && SwingUtilities.getWindowAncestor(component) == this;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(busyKeys);
        activityListener = event -> {
            if (session != null && !busy && event.getSource() instanceof Component component
                    && belongsToThisWindow(component)
                    && ((event instanceof KeyEvent key && key.getID() == KeyEvent.KEY_PRESSED)
                    || (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED))) {
                if (session.isActive()) { session.touch(); }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        expiryTimer = new Timer(1000, event -> {
            if (session != null && !session.isActive()) {
                logout();
                status.setText("Session expired. Sign in again.");
            }
        });
        expiryTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (busy) {
                    JOptionPane.showMessageDialog(AppFrame.this, "Please wait for the current operation to finish.");
                } else if (JOptionPane.showConfirmDialog(AppFrame.this, "Close SoftSecure? Unsaved changes will be lost.",
                        "Close application", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    dispose();
                }
            }
        });
        logout();
    }

    private boolean belongsToThisWindow(Component component) {
        Window window = component instanceof Window w ? w : SwingUtilities.getWindowAncestor(component);
        while (window != null) {
            if (window == this) { return true; }
            window = window.getOwner();
        }
        return false;
    }

    public AppServices services() { return services; }
    public Session session() { return session; }

    public void signedIn(Session authenticated) {
        session = authenticated;
        navigation.removeAll();
        JLabel brand = Theme.label("SOFTSECURE  /  " + session.role() + "  /  " + session.username());
        brand.setForeground(java.awt.Color.WHITE);
        navigation.add(brand, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(Theme.button("Dashboard", this::dashboard));
        buttons.add(Theme.button("Verify QR", () -> showPage(new VerificationPanel(this))));
        buttons.add(Theme.button("Change password", () -> showPage(new AccountPanel(this, false))));
        buttons.add(Theme.button("Sign out", this::logout));
        navigation.add(buttons, BorderLayout.CENTER);
        dashboard();
    }

    public void dashboard() {
        if (session != null) { showPage(new DashboardPanel(this)); }
    }

    public void logout() {
        if (session != null) { session.close(); }
        session = null;
        for (Window window : getOwnedWindows()) { window.dispose(); }
        navigation.removeAll();
        JLabel brand = Theme.label("SOFTSECURE  /  IDENTITY MANAGEMENT");
        brand.setForeground(java.awt.Color.WHITE);
        navigation.add(brand, BorderLayout.NORTH);
        showPage(new LoginPanel(this));
    }

    public void showPage(JComponent page) {
        generation++;
        getRootPane().setDefaultButton(null);
        content.removeAll();
        content.add(page, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
        navigation.revalidate();
        navigation.repaint();
        status.setText("Ready");
    }

    public void notifySuccess(String message) {
        status.setText(message);
        JOptionPane.showMessageDialog(this, Theme.text(message), "SoftSecure", JOptionPane.INFORMATION_MESSAGE);
    }

    public <T> boolean work(String description, Callable<T> task, Consumer<T> success) {
        if (busy) { return false; }
        if (session != null) {
            try { session.touch(); } catch (com.softsecure.id.service.UserException exception) { logout(); return false; }
        }
        long startedGeneration = generation;
        busy = true;
        busyLabel.setText(description);
        busyPane.setVisible(true);
        status.setText(description);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                busy = false;
                busyPane.setVisible(false);
                boolean current = generation == startedGeneration && isDisplayable();
                try {
                    T result = get();
                    if (current && (session == null || session.isActive())) {
                        status.setText("Ready");
                        success.accept(result);
                    } else if (result instanceof Session discarded) {
                        discarded.close();
                    } else if (current) {
                        logout();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (current) { Errors.show(AppFrame.this, exception); }
                } catch (ExecutionException exception) {
                    if (current) { Errors.show(AppFrame.this, exception.getCause()); }
                } catch (RuntimeException exception) {
                    if (current) { Errors.show(AppFrame.this, exception); }
                }
            }
        }.execute();
        return true;
    }

    @Override public void dispose() {
        if (session != null) { session.close(); }
        generation++;
        expiryTimer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(busyKeys);
        super.dispose();
    }
}
