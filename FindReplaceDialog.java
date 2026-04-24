import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

// ════════════════════════════════════════════════════════
//  Find & Replace Dialog
// ════════════════════════════════════════════════════════
class FindReplaceDialog extends JDialog {
    static class SearchResult {
        final int start, end;
        final String term;
        SearchResult(int s, int e, String t) { start=s; end=e; term=t; }
    }

    private final JTextField findField    = new JTextField(22);
    private final JTextField replaceField = new JTextField(22);
    private final JCheckBox  caseCheck    = new JCheckBox("Match Case");
    private final JLabel     resultLabel  = new JLabel(" ");
    private final JTextArea  targetArea;
    private Theme theme;

    public FindReplaceDialog(JFrame parent, JTextArea area, Theme t) {
        super(parent, "Find & Replace", false);
        this.targetArea = area;
        this.theme = t;
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(theme.getBackground());

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setBackground(theme.getBackground());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 4, 14));

        styleLabel(new JLabel("Find:"), form);
        styleField(findField, form);
        styleLabel(new JLabel("Replace:"), form);
        styleField(replaceField, form);
        form.add(new JLabel());
        caseCheck.setForeground(theme.getForeground());
        caseCheck.setBackground(theme.getBackground());
        form.add(caseCheck);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        btns.setBackground(theme.getBackground());

        JButton findBtn    = makeBtn("Find Next");
        JButton replBtn    = makeBtn("Replace");
        JButton replAllBtn = makeBtn("Replace All");
        JButton closeBtn   = makePlainBtn("Close");

        btns.add(findBtn); btns.add(replBtn);
        btns.add(replAllBtn); btns.add(closeBtn);

        resultLabel.setForeground(theme.getAccent());
        resultLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(theme.getBackground());
        south.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 14));
        south.add(btns, BorderLayout.CENTER);
        south.add(resultLabel, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        findBtn.addActionListener(e -> findNext());
        replBtn.addActionListener(e -> replaceOne());
        replAllBtn.addActionListener(e -> replaceAll());
        closeBtn.addActionListener(e -> dispose());

        setBackground(theme.getBackground());
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.getBorder(), 1));
    }

    private void styleLabel(JLabel lbl, JPanel p) {
        lbl.setForeground(theme.getForeground());
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(lbl);
    }

    private void styleField(JTextField f, JPanel p) {
        f.setBackground(theme.getSecondary());
        f.setForeground(theme.getForeground());
        f.setCaretColor(theme.getAccent());
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.getBorder()),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        f.setFont(new Font("Consolas", Font.PLAIN, 12));
        p.add(f);
    }

    private JButton makeBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(theme.getAccent());
        b.setForeground(new Color(15, 17, 21));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton makePlainBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(theme.getSecondary());
        b.setForeground(theme.getForeground());
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(theme.getBorder()));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void findNext() {
        try {
            String text  = targetArea.getText();
            String query = findField.getText();
            if (query.isEmpty()) throw new NotebookException("Search term is empty", "findNext");
            int from = targetArea.getCaretPosition();
            String t2 = caseCheck.isSelected() ? text  : text.toLowerCase();
            String q2 = caseCheck.isSelected() ? query : query.toLowerCase();
            int idx = t2.indexOf(q2, from);
            if (idx == -1) idx = t2.indexOf(q2, 0);
            if (idx == -1) {
                resultLabel.setText("  Not found: \"" + query + "\"");
            } else {
                targetArea.setCaretPosition(idx);
                targetArea.select(idx, idx + query.length());
                targetArea.requestFocus();
                resultLabel.setText("  Found at position " + idx);
            }
        } catch (NotebookException ex) {
            resultLabel.setText("  " + ex.getMessage());
        }
    }

    private void replaceOne() {
        try {
            String sel = targetArea.getSelectedText();
            String q   = findField.getText();
            if (q.isEmpty()) throw new NotebookException("Search term is empty", "replaceOne");
            boolean match = caseCheck.isSelected() ? q.equals(sel) : q.equalsIgnoreCase(sel);
            if (sel != null && match) targetArea.replaceSelection(replaceField.getText());
            findNext();
        } catch (NotebookException ex) {
            resultLabel.setText("  " + ex.getMessage());
        }
    }

    private void replaceAll() {
        try {
            String query = findField.getText();
            if (query.isEmpty()) throw new NotebookException("Search term is empty", "replaceAll");
            String text = targetArea.getText();
            String repl = replaceField.getText();
            int count; String result;
            if (caseCheck.isSelected()) {
                count  = (text.length() - text.replace(query, "").length()) / query.length();
                result = text.replace(query, repl);
            } else {
                count  = (text.toLowerCase().length()
                         - text.toLowerCase().replace(query.toLowerCase(), "").length()) / query.length();
                result = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(query), repl);
            }
            targetArea.setText(result);
            resultLabel.setText("  Replaced " + count + " occurrence(s)");
        } catch (NotebookException ex) {
            resultLabel.setText("  " + ex.getMessage());
        }
    }

    public void applyTheme(Theme t) { this.theme = t; }
}
