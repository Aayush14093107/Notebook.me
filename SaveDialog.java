import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class SaveDialog extends JDialog {
    private final Theme theme;
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
        this.theme = theme;
        this.currentDir = suggestedFile != null && suggestedFile.getParentFile() != null
            ? suggestedFile.getParentFile()
            : new File(System.getProperty("user.home"));

        setSize(580, 470);
        setLocationRelativeTo(parent);
        setResizable(true);
        setMinimumSize(new Dimension(580, 470));
        buildUI(parent, suggestedFile);
    }

    private void buildUI(JFrame parent, File suggestedFile) {
        GradientPanel root = new GradientPanel(
            new BorderLayout(),
            theme.getBackground(),
            theme.getBackground(),
            null,
            0);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        SurfacePanel shell = new SurfacePanel(
            new BorderLayout(0, 0),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        root.add(shell, BorderLayout.CENTER);

        GradientPanel titleBar = new GradientPanel(
            new BorderLayout(),
            ModernUI.panelColor(theme),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        titleBar.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Save file");
        title.setFont(ModernUI.uiFont(Font.BOLD, 18f));
        title.setForeground(theme.getForeground());
        JLabel subtitle = new JLabel("Pick a folder, a filename, and the right extension.");
        subtitle.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        subtitle.setForeground(ModernUI.withAlpha(theme.getForeground(), 165));
        titleStack.add(title);
        titleStack.add(Box.createVerticalStrut(4));
        titleStack.add(subtitle);
        titleBar.add(titleStack, BorderLayout.WEST);
        shell.add(titleBar, BorderLayout.NORTH);

        JPanel content = ModernUI.transparentPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        JPanel pathPanel = ModernUI.transparentPanel(new BorderLayout(8, 0));
        JLabel pathLabel = new JLabel("Location");
        pathLabel.setForeground(theme.getForeground());
        pathLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        pathField = new JTextField(currentDir.getAbsolutePath());
        ModernUI.styleTextField(pathField, theme, false);
        JButton browseBtn = new JButton("Browse");
        ModernUI.styleButton(browseBtn, theme, "secondary");
        browseBtn.addActionListener(e -> browseFolder(parent));
        pathPanel.add(pathLabel, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseBtn, BorderLayout.EAST);

        folderModel = new DefaultListModel<>();
        folderList = new JList<>(folderModel);
        ModernUI.styleList(folderList, theme);
        folderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                String name = String.valueOf(value);
                if ("..".equals(name)) {
                    label.setText("Up one level");
                } else {
                    File file = new File(currentDir, name);
                    label.setText((file.isDirectory() ? "Folder  " : "File  ") + name);
                }
                label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                label.setBackground(selected ? ModernUI.accentSoft(theme) : ModernUI.panelColor(theme));
                label.setForeground(theme.getForeground());
                return label;
            }
        });
        folderList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateFolder(folderList.getSelectedValue());
                }
            }
        });
        JScrollPane folderScroll = new JScrollPane(folderList);
        ModernUI.styleScrollPane(folderScroll, theme, ModernUI.panelColor(theme));
        folderScroll.setBorder(new RoundedBorder(ModernUI.hairline(theme), ModernUI.RADIUS, 1));
        folderScroll.setPreferredSize(new Dimension(0, 210));

        JPanel filePanel = ModernUI.transparentPanel(new BorderLayout(8, 0));
        JLabel nameLabel = new JLabel("File name");
        nameLabel.setForeground(theme.getForeground());
        nameLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        fileNameField = new JTextField(suggestedFile != null ? stripExt(suggestedFile.getName()) : "untitled");
        ModernUI.styleTextField(fileNameField, theme, false);
        extensionBox = new JComboBox<>(new String[]{".txt", ".md", ".java", ".py", ".html", ".css", ".js"});
        ModernUI.styleComboBox(extensionBox, theme);
        if (suggestedFile != null) {
            String ext = getExt(suggestedFile.getName());
            if (!ext.isEmpty()) extensionBox.setSelectedItem("." + ext);
        }
        filePanel.add(nameLabel, BorderLayout.WEST);
        filePanel.add(fileNameField, BorderLayout.CENTER);
        filePanel.add(extensionBox, BorderLayout.EAST);

        content.add(pathPanel, BorderLayout.NORTH);
        content.add(folderScroll, BorderLayout.CENTER);
        content.add(filePanel, BorderLayout.SOUTH);
        shell.add(content, BorderLayout.CENTER);

        JPanel footer = ModernUI.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ModernUI.hairline(theme)),
            BorderFactory.createEmptyBorder(10, 14, 12, 14)));
        JButton cancelBtn = new JButton("Cancel");
        JButton saveBtn = new JButton("Save");
        ModernUI.styleButton(cancelBtn, theme, "secondary");
        ModernUI.styleButton(saveBtn, theme, "primary");
        cancelBtn.addActionListener(e -> { approved = false; dispose(); });
        saveBtn.addActionListener(e -> doSave());
        footer.add(cancelBtn);
        footer.add(saveBtn);
        shell.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveBtn);

        loadFolder();
    }

    private void navigateFolder(String selection) {
        if (selection == null) return;
        if (selection.equals("..")) {
            File parent = currentDir.getParentFile();
            if (parent != null) {
                currentDir = parent;
                loadFolder();
            }
            return;
        }
        File picked = new File(currentDir, selection);
        if (picked.isDirectory()) {
            currentDir = picked;
            loadFolder();
        } else if (picked.isFile()) {
            fileNameField.setText(stripExt(picked.getName()));
            String ext = getExt(picked.getName());
            if (!ext.isEmpty()) extensionBox.setSelectedItem("." + ext);
        }
    }

    private void loadFolder() {
        folderModel.clear();
        pathField.setText(currentDir.getAbsolutePath());
        if (currentDir.getParentFile() != null) folderModel.addElement("..");
        File[] files = currentDir.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File file : files) {
            if (file.isHidden()) continue;
            folderModel.addElement(file.getName());
        }
    }

    private void doSave() {
        String name = fileNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "File name is required.");
            return;
        }
        String ext = (String) extensionBox.getSelectedItem();
        if (!name.contains(".")) name += ext;
        selectedFile = new File(currentDir, name);
        if (selectedFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "\"" + name + "\" already exists.\nOverwrite it?",
                "Confirm overwrite",
                JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        approved = true;
        dispose();
    }

    private void browseFolder(JFrame parent) {
        JFileChooser chooser = new JFileChooser(currentDir);
        SwingUtilities.updateComponentTreeUI(chooser);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            currentDir = chooser.getSelectedFile();
            loadFolder();
        }
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    public File getSelectedFile() { return selectedFile; }
    public boolean isApproved() { return approved; }
}
