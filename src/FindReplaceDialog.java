import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

class FindReplaceDialog extends JDialog {
    private final JTextField findField = new JTextField(22);
    private final JTextField replaceField = new JTextField(22);
    private final JCheckBox caseCheck = new JCheckBox("Match Case");
    private final JLabel resultLabel = new JLabel("Ready");
    private final JTextArea targetArea;
    private Theme theme;

    public FindReplaceDialog(JFrame parent, JTextArea area, Theme theme) {
        super(parent, "Find & Replace", false);
        this.targetArea = area;
        this.theme = theme;
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        GradientPanel root = new GradientPanel(
            new BorderLayout(0, 8),
            theme.getBackground(),
            theme.getBackground(),
            null,
            0);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        GradientPanel header = new GradientPanel(
            new BorderLayout(),
            ModernUI.panelColor(theme),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel title = new JLabel("Find & replace");
        title.setFont(ModernUI.uiFont(Font.BOLD, 18f));
        title.setForeground(theme.getForeground());
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        header.add(heading, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        SurfacePanel content = new SurfacePanel(
            new BorderLayout(0, 12),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 10));
        form.setOpaque(false);
        form.add(label("Find"));
        ModernUI.styleTextField(findField, theme, false);
        form.add(findField);
        form.add(label("Replace"));
        ModernUI.styleTextField(replaceField, theme, false);
        form.add(replaceField);
        form.add(new JLabel());
        ModernUI.styleCheckBox(caseCheck, theme);
        form.add(caseCheck);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton findBtn = button("Find Next", "secondary");
        JButton replaceBtn = button("Replace", "secondary");
        JButton replaceAllBtn = button("Replace All", "primary");
        JButton closeBtn = button("Close", "ghost");
        actions.add(closeBtn);
        actions.add(findBtn);
        actions.add(replaceBtn);
        actions.add(replaceAllBtn);

        resultLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        resultLabel.setForeground(ModernUI.mix(theme.getForeground(), theme.getAccent(), 0.18f));

        content.add(form, BorderLayout.NORTH);
        content.add(actions, BorderLayout.CENTER);
        content.add(resultLabel, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        findBtn.addActionListener(e -> findNext());
        replaceBtn.addActionListener(e -> replaceOne());
        replaceAllBtn.addActionListener(e -> replaceAll());
        closeBtn.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(findBtn);
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(theme.getForeground());
        label.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        return label;
    }

    private JButton button(String text, String variant) {
        JButton button = new JButton(text);
        ModernUI.styleButton(button, theme, variant);
        return button;
    }

    private void findNext() {
        try {
            String text = targetArea.getText();
            String query = findField.getText();
            if (query.isEmpty()) throw new NotebookException("Search term is empty", "findNext");
            int from = targetArea.getCaretPosition();
            String target = caseCheck.isSelected() ? text : text.toLowerCase();
            String match = caseCheck.isSelected() ? query : query.toLowerCase();
            int index = target.indexOf(match, from);
            if (index == -1) index = target.indexOf(match, 0);
            if (index == -1) {
                resultLabel.setText("No match for \"" + query + "\"");
                return;
            }
            targetArea.setCaretPosition(index);
            targetArea.select(index, index + query.length());
            targetArea.requestFocus();
            resultLabel.setText("Found at character " + index);
        } catch (NotebookException ex) {
            resultLabel.setText(ex.getMessage());
        }
    }

    private void replaceOne() {
        try {
            String selected = targetArea.getSelectedText();
            String query = findField.getText();
            if (query.isEmpty()) throw new NotebookException("Search term is empty", "replaceOne");
            boolean matches = caseCheck.isSelected() ? query.equals(selected) : query.equalsIgnoreCase(selected);
            if (selected != null && matches) {
                targetArea.replaceSelection(replaceField.getText());
            }
            findNext();
        } catch (NotebookException ex) {
            resultLabel.setText(ex.getMessage());
        }
    }

    private void replaceAll() {
        try {
            String query = findField.getText();
            if (query.isEmpty()) throw new NotebookException("Search term is empty", "replaceAll");
            String replacement = replaceField.getText();
            String text = targetArea.getText();
            int count;
            String result;
            if (caseCheck.isSelected()) {
                count = (text.length() - text.replace(query, "").length()) / query.length();
                result = text.replace(query, replacement);
            } else {
                count = (text.toLowerCase().length() - text.toLowerCase().replace(query.toLowerCase(), "").length()) / query.length();
                result = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(query), java.util.regex.Matcher.quoteReplacement(replacement));
            }
            targetArea.setText(result);
            resultLabel.setText("Replaced " + count + " occurrence(s)");
        } catch (NotebookException ex) {
            resultLabel.setText(ex.getMessage());
        }
    }

    public void applyTheme(Theme theme) {
        this.theme = theme;
    }
}
