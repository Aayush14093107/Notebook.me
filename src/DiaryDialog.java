import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

class DiaryDialog extends JDialog {
    private static final String ENCRYPTED_ENTRY_HEADER = "NOTEBOOKME_DIARY_AES128_V1\n";
    private static final int AES_KEY_BYTES = 16;
    private static final int AES_BLOCK_BYTES = 16;
    private static final String[] SECURITY_QUESTIONS = {
        "What is your pet's name?",
        "What city were you born in?",
        "What is your favorite book?",
        "What was your first school?",
        "What is your mother's maiden name?"
    };

    private final File diaryDir;
    private final Theme theme;
    private JList<String> dateList;
    private DefaultListModel<String> listModel;
    private JTextArea entryArea;
    private String currentDate;
    private JLabel countLabel;
    private SecretKeySpec diaryKey;

    public DiaryDialog(JFrame parent, Theme theme, File notebookDir) {
        super(parent, "Diary Mode", true);
        this.theme = theme;
        this.diaryDir = new File(notebookDir, "diary");
        if (!diaryDir.exists()) diaryDir.mkdirs();

        if (!authenticate()) {
            dispose();
            return;
        }

        setSize(780, 560);
        setLocationRelativeTo(parent);
        buildUI();
        loadEntries();
    }

    private boolean authenticate() {
        File pinFile = new File(diaryDir, ".pin");
        File securityFile = new File(diaryDir, ".security");

        if (!pinFile.exists()) {
            JPasswordField pinOne = new JPasswordField(10);
            JPasswordField pinTwo = new JPasswordField(10);
            JComboBox<String> questionBox = new JComboBox<>(SECURITY_QUESTIONS);
            JTextField answerField = new JTextField(15);
            ModernUI.styleComboBox(questionBox, theme);
            ModernUI.styleTextField(answerField, theme, false);

            JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
            panel.add(new JLabel("Create a PIN for your diary:"));
            panel.add(pinOne);
            panel.add(pinTwo);
            panel.add(new JLabel("Security question (for PIN reset):"));
            panel.add(questionBox);
            panel.add(answerField);

            int choice = JOptionPane.showConfirmDialog(this, panel, "Set Diary PIN", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return false;

            String first = new String(pinOne.getPassword());
            String second = new String(pinTwo.getPassword());
            String answer = answerField.getText().trim();
            if (first.isEmpty() || !first.equals(second)) {
                JOptionPane.showMessageDialog(this, "PINs do not match or are empty.");
                return false;
            }
            if (answer.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please provide a security answer.");
                return false;
            }

            try {
                Files.writeString(pinFile.toPath(), hashPin(first));
                Files.writeString(securityFile.toPath(), questionBox.getSelectedIndex() + "\n" + hashPin(answer.toLowerCase()));
                diaryKey = deriveAESKey(first);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }

        JPasswordField pinField = new JPasswordField(10);
        JPanel panel = new JPanel(new BorderLayout(4, 8));
        JPanel fields = new JPanel(new GridLayout(2, 1, 4, 4));
        fields.add(new JLabel("Enter your diary PIN:"));
        fields.add(pinField);
        panel.add(fields, BorderLayout.CENTER);

        JButton forgotBtn = new JButton("Forgot PIN?");
        forgotBtn.setFont(ModernUI.uiFont(Font.PLAIN, 11f));
        forgotBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgotBtn.setForeground(new Color(70, 130, 220));
        forgotBtn.setContentAreaFilled(false);
        forgotBtn.setBorderPainted(false);
        final boolean[] forgotPINHandled = {false};
        forgotBtn.addActionListener(e -> {
            forgotPINHandled[0] = handleForgotPIN(pinFile, securityFile);
            if (forgotPINHandled[0]) {
                Window window = SwingUtilities.getWindowAncestor(forgotBtn);
                if (window != null) window.dispose();
            }
        });

        JButton resetBtn = new JButton("Reset Diary");
        resetBtn.setFont(ModernUI.uiFont(Font.PLAIN, 11f));
        resetBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        resetBtn.setForeground(new Color(220, 70, 70));
        resetBtn.setContentAreaFilled(false);
        resetBtn.setBorderPainted(false);
        resetBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(panel,
                "This will DELETE all diary entries and reset your PIN.\nThis cannot be undone!\n\nAre you sure?",
                "Reset Diary", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                if (diaryDir.exists()) {
                    File[] files = diaryDir.listFiles();
                    if (files != null) for (File f : files) f.delete();
                }
                JOptionPane.showMessageDialog(panel, "Diary reset complete");
                Window window = SwingUtilities.getWindowAncestor(resetBtn);
                if (window != null) window.dispose();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(forgotBtn);
        btnPanel.add(resetBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(this, panel, "Diary PIN", JOptionPane.OK_CANCEL_OPTION);
        if (forgotPINHandled[0]) return true;
        if (choice == JOptionPane.CLOSED_OPTION) {
            return false;
        }
        if (choice != JOptionPane.OK_OPTION) return false;

        try {
            String stored = Files.readString(pinFile.toPath()).trim();
            String enteredPIN = new String(pinField.getPassword());
            String entered = hashPin(enteredPIN);
            if (!stored.equals(entered)) {
                JOptionPane.showMessageDialog(this, "Wrong PIN!", "Access denied", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            diaryKey = deriveAESKey(enteredPIN);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean handleForgotPIN(File pinFile, File securityFile) {
        if (!securityFile.exists()) {
            JOptionPane.showMessageDialog(this, "No security question is set. PIN reset is unavailable.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            String[] securityData = Files.readString(securityFile.toPath()).trim().split("\n");
            int questionIndex = Integer.parseInt(securityData[0]);
            String storedHash = securityData[1].trim();

            JTextField answerField = new JTextField(15);
            ModernUI.styleTextField(answerField, theme, false);
            JPanel answerPanel = new JPanel(new GridLayout(0, 1, 4, 4));
            answerPanel.add(new JLabel("Security question:"));
            answerPanel.add(new JLabel(SECURITY_QUESTIONS[questionIndex]));
            answerPanel.add(answerField);

            int answerChoice = JOptionPane.showConfirmDialog(this, answerPanel, "Reset PIN", JOptionPane.OK_CANCEL_OPTION);
            if (answerChoice != JOptionPane.OK_OPTION) return false;

            if (!hashPin(answerField.getText().trim().toLowerCase()).equals(storedHash)) {
                JOptionPane.showMessageDialog(this, "Wrong answer!", "Access denied", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            JPasswordField newPinOne = new JPasswordField(10);
            JPasswordField newPinTwo = new JPasswordField(10);
            JPanel newPinPanel = new JPanel(new GridLayout(0, 1, 4, 4));
            newPinPanel.add(new JLabel("Set a new PIN:"));
            newPinPanel.add(newPinOne);
            newPinPanel.add(newPinTwo);

            int pinChoice = JOptionPane.showConfirmDialog(this, newPinPanel, "New PIN", JOptionPane.OK_CANCEL_OPTION);
            if (pinChoice != JOptionPane.OK_OPTION) return false;

            String first = new String(newPinOne.getPassword());
            String second = new String(newPinTwo.getPassword());
            if (first.isEmpty() || !first.equals(second)) {
                JOptionPane.showMessageDialog(this, "PINs do not match or are empty.");
                return false;
            }

            Files.writeString(pinFile.toPath(), hashPin(first));
            diaryKey = deriveAESKey(first);
            JOptionPane.showMessageDialog(this, "PIN reset successfully.");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to reset PIN.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) builder.append(String.format("%02x", value));
            return builder.toString();
        } catch (Exception ex) {
            return pin;
        }
    }

    private SecretKeySpec deriveAESKey(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(hash, AES_KEY_BYTES), "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive diary encryption key.", ex);
        }
    }

    private byte[] encryptEntry(String text) throws Exception {
        if (diaryKey == null) throw new IllegalStateException("Diary encryption key is unavailable.");

        byte[] iv = new byte[AES_BLOCK_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, diaryKey, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

        String encoded = ENCRYPTED_ENTRY_HEADER + Base64.getEncoder().encodeToString(payload);
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    private String decryptEntry(byte[] fileBytes) throws Exception {
        if (diaryKey == null) throw new IllegalStateException("Diary encryption key is unavailable.");

        String stored = new String(fileBytes, StandardCharsets.UTF_8);
        if (!stored.startsWith(ENCRYPTED_ENTRY_HEADER)) {
            throw new IllegalArgumentException("Entry is not encrypted.");
        }

        byte[] payload = Base64.getDecoder().decode(stored.substring(ENCRYPTED_ENTRY_HEADER.length()).trim());
        if (payload.length <= AES_BLOCK_BYTES) {
            throw new IllegalArgumentException("Encrypted entry is incomplete.");
        }

        byte[] iv = Arrays.copyOfRange(payload, 0, AES_BLOCK_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, AES_BLOCK_BYTES, payload.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, diaryKey, new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private String readEntryText(File entryFile) throws IOException {
        byte[] fileBytes = Files.readAllBytes(entryFile.toPath());
        try {
            return decryptEntry(fileBytes);
        } catch (Exception ex) {
            return Files.readString(entryFile.toPath());
        }
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

        GradientPanel topBar = new GradientPanel(
            new BorderLayout(),
            ModernUI.panelColor(theme),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Private diary");
        title.setFont(ModernUI.uiFont(Font.BOLD, 18f));
        title.setForeground(theme.getForeground());
        titleStack.add(title);
        topBar.add(titleStack, BorderLayout.WEST);

        JLabel streakLabel = new JLabel(calculateStreak());
        streakLabel.setOpaque(true);
        streakLabel.setBackground(ModernUI.accentSoft(theme));
        streakLabel.setForeground(theme.getForeground());
        streakLabel.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(ModernUI.hairline(theme), ModernUI.RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        streakLabel.setFont(ModernUI.uiFont(Font.BOLD, 11f));

        JPanel actions = ModernUI.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton todayBtn = button("Today", "secondary");
        JButton saveBtn = button("Save", "primary");
        JButton deleteBtn = button("Delete", "danger");
        JButton closeBtn = button("Close", "ghost");
        todayBtn.addActionListener(e -> openToday());
        saveBtn.addActionListener(e -> { saveEntry(); streakLabel.setText(calculateStreak()); });
        deleteBtn.addActionListener(e -> deleteEntry());
        closeBtn.addActionListener(e -> { saveEntry(); dispose(); });
        actions.add(streakLabel);
        actions.add(todayBtn);
        actions.add(saveBtn);
        actions.add(deleteBtn);
        actions.add(closeBtn);
        topBar.add(actions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        dateList = new JList<>(listModel);
        ModernUI.styleList(dateList, theme);
        dateList.setFont(ModernUI.monoFont(Font.PLAIN, 13f));
        dateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadEntry(dateList.getSelectedValue());
        });
        JScrollPane listScroll = new JScrollPane(dateList);
        ModernUI.styleScrollPane(listScroll, theme, ModernUI.panelColor(theme));

        entryArea = new JTextArea();
        entryArea.setLineWrap(true);
        entryArea.setWrapStyleWord(true);
        entryArea.setFont(new Font("Georgia", Font.PLAIN, 14));
        entryArea.setBackground(ModernUI.editorColor(theme));
        entryArea.setForeground(theme.getForeground());
        entryArea.setCaretColor(theme.getAccent());
        entryArea.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        entryArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateEntryCount(); }
            public void removeUpdate(DocumentEvent e) { updateEntryCount(); }
            public void changedUpdate(DocumentEvent e) { updateEntryCount(); }
        });
        JScrollPane editScroll = new JScrollPane(entryArea);
        ModernUI.styleScrollPane(editScroll, theme, ModernUI.editorColor(theme));
        countLabel = new JLabel();
        countLabel.setHorizontalAlignment(SwingConstants.LEFT);
        countLabel.setFont(ModernUI.monoFont(Font.PLAIN, 12f));
        countLabel.setForeground(ModernUI.mix(theme.getForeground(), theme.getAccent(), 0.14f));
        countLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));
        updateEntryCount();

        SurfacePanel listShell = new SurfacePanel(new BorderLayout(), ModernUI.panelColor(theme), ModernUI.hairline(theme), ModernUI.RADIUS);
        listShell.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        listShell.add(listScroll, BorderLayout.CENTER);

        SurfacePanel editShell = new SurfacePanel(new BorderLayout(), ModernUI.panelColor(theme), ModernUI.hairline(theme), ModernUI.RADIUS);
        editShell.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        editShell.add(editScroll, BorderLayout.CENTER);
        editShell.add(countLabel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listShell, editShell);
        split.setDividerLocation(190);
        split.setDividerSize(1);
        ModernUI.styleSplitPane(split, theme);
        root.add(split, BorderLayout.CENTER);
    }

    private JButton button(String text, String variant) {
        JButton button = new JButton(text);
        ModernUI.styleButton(button, theme, variant);
        return button;
    }

    private void loadEntries() {
        listModel.clear();
        File[] files = diaryDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
            for (File file : files) {
                listModel.addElement(file.getName().replace(".txt", ""));
            }
        }
        openToday();
    }

    private void openToday() {
        currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (!listModel.contains(currentDate)) listModel.add(0, currentDate);
        dateList.setSelectedValue(currentDate, true);
        loadEntry(currentDate);
    }

    private void loadEntry(String date) {
        if (date == null) return;
        currentDate = date;
        File entryFile = new File(diaryDir, date + ".txt");
        if (entryFile.exists()) {
            try {
                entryArea.setText(readEntryText(entryFile));
                entryArea.setCaretPosition(0);
            } catch (IOException ex) {
                entryArea.setText("");
            }
        } else {
            entryArea.setText("Dear Diary,\n\n");
            entryArea.setCaretPosition(entryArea.getText().length());
        }
    }

    private void saveEntry() {
        if (currentDate == null) return;
        File entryFile = new File(diaryDir, currentDate + ".txt");
        try {
            Files.write(entryFile.toPath(), encryptEntry(entryArea.getText()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save entry.");
        }
    }

    private void updateEntryCount() {
        if (entryArea == null || countLabel == null) return;
        String text = entryArea.getText();
        String trimmed = text.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        countLabel.setText(String.format(Locale.US, "%,d words \u00b7 %,d characters", words, text.length()));
    }

    private void deleteEntry() {
        if (currentDate == null) return;
        int choice = JOptionPane.showConfirmDialog(this, "Delete entry for " + currentDate + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            new File(diaryDir, currentDate + ".txt").delete();
            loadEntries();
        }
    }

    private String calculateStreak() {
        int streak = 0;
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < 365; i++) {
            String dateString = formatter.format(calendar.getTime());
            File entryFile = new File(diaryDir, dateString + ".txt");
            if (entryFile.exists() && entryFile.length() > 0) {
                streak++;
            } else if (i > 0) {
                break;
            }
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        if (streak == 0) return "Start your streak today";
        return streak + "-day streak";
    }
}
