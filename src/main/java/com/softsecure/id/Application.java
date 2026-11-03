package com.softsecure.id;

import com.softsecure.id.ui.AppFrame;
import com.softsecure.id.ui.Errors;
import com.softsecure.id.ui.Theme;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class Application {
    private Application() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            JFrame loading = new JFrame("SoftSecure · Starting");
            loading.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            loading.add(Theme.page("SoftSecure ID Generator", "Loading configuration and security services…"));
            loading.setSize(600, 210);
            loading.setLocationRelativeTo(null);
            loading.setVisible(true);
            new SwingWorker<AppServices, Void>() {
                @Override protected AppServices doInBackground() throws Exception { return AppServices.load(); }
                @Override protected void done() {
                    try {
                        AppServices services = get();
                        if (loading.isDisplayable()) { new AppFrame(services).setVisible(true); }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        Errors.show(loading, exception);
                    } catch (java.util.concurrent.ExecutionException exception) {
                        Errors.show(loading, exception.getCause());
                    } finally { loading.dispose(); }
                }
            }.execute();
        });
    }
}
