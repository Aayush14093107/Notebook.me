/**
 * SaveDialog.java — Custom themed Save dialog for notebook.me v5.0.0
 */
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

public class SaveDialog extends JDialog {
    private File selectedFile;
    private boolean approved = false;
    private JTextField fileNameField;
    private JComboBox<String> extensionBox;
    private JTextField pathField;
    private JList<String> folderList;
    private DefaultListModel<String> folderModel;
    private File currentDir;

    public SaveDialog(JFrame parent, Theme theme, File suggestedFile) {
        super(parent, "Save As", true);
        this.currentDir = suggestedFile != null && suggestedFile.getParentFile() != null
            ? suggestedFile.getParentFile() : new File(System.getProperty("user.home"));

        Color bg = theme.getMenuBg(), fg = theme.getForeground(), ac = theme.getAccent();
        Color inputBg = theme.getSecondary(), border = theme.getBorder();

        setSize(560, 440);
        setLocationRelativeTo(parent);
        setResizable(false);
        getContentPane().setBackground(bg);
        setLayout(new BorderLayout(0, 0));

        // ── Title Bar ──
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(ac);
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel titleLabel = new JLabel("💾  Save File");
        titleLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 15));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.WEST);
        add(titleBar, BorderLayout.NORTH);

        // ── Main Content ──
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(bg);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        // Path bar
        JPanel pathPanel = new JPanel(new BorderLayout(6, 0));
        pathPanel.setOpaque(false);
        JLabel pathLabel = new JLabel("Location:");
        pathLabel.setForeground(fg); pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pathField = new JTextField(currentDir.getAbsolutePath());
        styleTextField(pathField, inputBg, fg, border);
        JButton browseBtn = styledButton("Browse", ac, Color.WHITE);
        browseBtn.addActionListener(e -> browseFolder(parent, theme));
        pathPanel.add(pathLabel, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseBtn, BorderLayout.EAST);

        // Folder browser
        folderModel = new DefaultListModel<>();
        folderList = new JList<>(folderModel);
        folderList.setBackground(inputBg); folderList.setForeground(fg);
        folderList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        folderList.setSelectionBackground(ac); folderList.setSelectionForeground(Color.WHITE);
        folderList.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        folderList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = folderList.getSelectedValue();
                    if (sel != null) {
                        if (sel.equals("📁 ..")) {
                            File parent = currentDir.getParentFile();
                            if (parent != null) { currentDir = parent; loadFolder(); }
                        } else if (sel.startsWith("📁")) {
                            String name = sel.substring(3).trim();
                            File dir = new File(currentDir, name);
                            if (dir.isDirectory()) { currentDir = dir; loadFolder(); }
                        }
                    }
                }
            }
        });
        JScrollPane folderScroll = new JScrollPane(folderList);
        folderScroll.setBorder(BorderFactory.createLineBorder(border));
        folderScroll.setPreferredSize(new Dimension(0, 180));
        loadFolder();

        // File name & extension
        JPanel filePanel = new JPanel(new BorderLayout(8, 0));
        filePanel.setOpaque(false);
        JLabel nameLabel = new JLabel("File name:");
        nameLabel.setForeground(fg); nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fileNameField = new JTextField(suggestedFile != null ? stripExt(suggestedFile.getName()) : "untitled");
        styleTextField(fileNameField, inputBg, fg, border);
        extensionBox = new JComboBox<>(new String[]{".txt", ".md", ".java", ".py", ".html", ".css", ".js"});
        extensionBox.setBackground(inputBg); extensionBox.setForeground(fg);
        extensionBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        extensionBox.setBorder(BorderFactory.createLineBorder(border));
        if (suggestedFile != null) {
            String ext = getExt(suggestedFile.getName());
            if (!ext.isEmpty()) extensionBox.setSelectedItem("." + ext);
        }
        JPanel nameRow = new JPanel(new BorderLayout(6, 0)); nameRow.setOpaque(false);
        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(fileNameField, BorderLayout.CENTER);
        nameRow.add(extensionBox, BorderLayout.EAST);
        filePanel.add(nameRow, BorderLayout.CENTER);

        content.add(pathPanel, BorderLayout.NORTH);
        content.add(folderScroll, BorderLayout.CENTER);
        content.add(filePanel, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        // ── Button Bar ──
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setBackground(bg);
        btnBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, border));
        JButton saveBtn = styledButton("💾  Save", ac, Color.WHITE);
        saveBtn.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
        saveBtn.setPreferredSize(new Dimension(120, 34));
        JButton cancelBtn = styledButton("Cancel", inputBg, fg);
        cancelBtn.setPreferredSize(new Dimension(90, 34));
        saveBtn.addActionListener(e -> doSave());
        cancelBtn.addActionListener(e -> { approved = false; dispose(); });
        btnBar.add(cancelBtn); btnBar.add(saveBtn);
        add(btnBar, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveBtn);
    }

    private void loadFolder() {
        folderModel.clear();
        pathField.setText(currentDir.getAbsolutePath());
        if (currentDir.getParentFile() != null) folderModel.addElement("📁 ..");
        File[] files = currentDir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                if (f.isHidden()) continue;
                folderModel.addElement(f.isDirectory() ? "📁 " + f.getName() : "📄 " + f.getName());
            }
        }
    }

    private void doSave() {
        String name = fileNameField.getText().trim();
        if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "File name is required"); return; }
        String ext = (String) extensionBox.getSelectedItem();
        if (!name.contains(".")) name += ext;
        selectedFile = new File(currentDir, name);
        if (selectedFile.exists()) {
            int r = JOptionPane.showConfirmDialog(this, "\"" + name + "\" already exists.\nOverwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }
        approved = true;
        dispose();
    }

    private void browseFolder(JFrame parent, Theme theme) {
        JFileChooser ch = new JFileChooser(currentDir);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentDir = ch.getSelectedFile();
            loadFolder();
        }
    }

    private void styleTextField(JTextField tf, Color bg, Color fg, Color border) {
        tf.setBackground(bg); tf.setForeground(fg); tf.setCaretColor(fg);
        tf.setFont(new Font("Consolas", Font.PLAIN, 12));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    private JButton styledButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(fg);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name;
    }
    private String getExt(String name) {
        int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(dot + 1) : "";
    }

    public File getSelectedFile() { return selectedFile; }
    public boolean isApproved() { return approved; }
}
