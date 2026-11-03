package com.softsecure.id.ui;

import com.softsecure.id.domain.RequestSummary;
import com.softsecure.id.domain.Status;
import com.softsecure.id.security.Session;
import com.softsecure.id.service.UserException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

public final class ReviewPanel extends JPanel {
    private final AppFrame frame;
    private final Session session;
    private final JComboBox<Status> filter = new JComboBox<>(Status.values());
    private final DefaultTableModel model = new DefaultTableModel(new String[] {"Request reference", "Role", "Status", "Version", "Submitted (UTC)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<RequestSummary> rows = List.of();
    private int offset;

    public ReviewPanel(AppFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        session = frame.session();
        JPanel page = Theme.page("Administrative review", "Open a request to inspect all details before approving or rejecting it. Pages contain up to 50 records.");
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(Theme.label("Status"));
        filter.getAccessibleContext().setAccessibleName("Filter by request status");
        toolbar.add(filter);
        filter.addActionListener(event -> { offset = 0; refresh(); });
        toolbar.add(Theme.button("Refresh", this::refresh));
        toolbar.add(Theme.button("Previous", () -> { offset = Math.max(0, offset - 50); refresh(); }));
        toolbar.add(Theme.button("Next", () -> { if (rows.size() == 50) { offset += 50; refresh(); } }));
        toolbar.add(Theme.button("Open selected request", this::openSelected));
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getAccessibleContext().setAccessibleName("Identity request review queue");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.add(toolbar, BorderLayout.NORTH);
        body.add(new JScrollPane(table), BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    public void refresh() {
        Status selected = (Status) filter.getSelectedItem();
        int pageOffset = offset;
        frame.work("Loading review queue…", () -> frame.services().ids().queue(session, selected, pageOffset), result -> {
            rows = result;
            model.setRowCount(0);
            for (RequestSummary row : rows) {
                model.addRow(new Object[] {row.id(), row.role(), row.status(), row.version(), row.submittedAt()});
            }
        });
    }

    private void openSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            Errors.show(frame, new UserException("Select a request from the table first."));
            return;
        }
        String id = rows.get(table.convertRowIndexToModel(selected)).id();
        frame.work("Loading request details…", () -> frame.services().ids().inspect(session, id),
                request -> CardPanel.open(frame, request, false));
    }
}
