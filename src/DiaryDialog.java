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
    private static final String SEARCH_PLACEHOLDER = "Search entries...";
    private static final String TAG_PLACEHOLDER = "Add tags e.g. #rants #travel";
    private static final String[] MOODS = {
        "\uD83D\uDE04", "\uD83D\uDE0A", "\uD83D\uDE10", "\uD83D\uDE14", "\uD83D\uDE22"
    };
    private static final String[] QUICK_TAGS = {
        "#rants", "#travel", "#grateful", "#milestone", "#venting", "#reflection"
    };
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
    private JTextField searchField;
    private JTextField tagField;
    private JComboBox<String> tagFilterBox;
    private String currentDate;
    private JLabel countLabel;
    private SecretKeySpec diaryKey;
    private String selectedMood;
    private boolean updatingTagFilter;
    private boolean suppressListSelection;
    private final java.util.List<JButton> moodButtons = new ArrayList<>();
    private final Map<String, String> tagPreviewCache = new HashMap<>();

    public DiaryDialog(JFrame parent, Theme theme, File notebookDir) {
        super(parent, "Diary Mode", true);
        this.theme = theme;
        this.diaryDir = new File(notebookDir, "diary");
        if (!diaryDir.exists()) diaryDir.mkdirs();

        if (!authenticate()) {
            dispose();
            return;
        }

        setSize(980, 640);
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
            new BorderLayout(12, 0),
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

        searchField = new JTextField();
        ModernUI.styleTextField(searchField, theme, false);
        searchField.setPreferredSize(new Dimension(190, 32));
        installPlaceholder(searchField, SEARCH_PLACEHOLDER);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyEntryFilters(); }
            public void removeUpdate(DocumentEvent e) { applyEntryFilters(); }
            public void changedUpdate(DocumentEvent e) { applyEntryFilters(); }
        });

        JLabel tagFilterLabel = new JLabel("Tag:");
        tagFilterLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        tagFilterLabel.setForeground(theme.getForeground());
        tagFilterBox = new JComboBox<>();
        ModernUI.styleComboBox(tagFilterBox, theme);
        tagFilterBox.setPreferredSize(new Dimension(135, 32));
        tagFilterBox.addActionListener(e -> {
            if (!updatingTagFilter) applyEntryFilters();
        });

        JPanel filters = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(searchField);
        filters.add(tagFilterLabel);
        filters.add(tagFilterBox);
        topBar.add(filters, BorderLayout.CENTER);

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
        JButton exportBtn = button("Export", "secondary");
        JButton deleteBtn = button("Delete", "danger");
        JButton closeBtn = button("Close", "ghost");
        todayBtn.addActionListener(e -> openToday());
        saveBtn.addActionListener(e -> { saveEntry(); streakLabel.setText(calculateStreak()); });
        exportBtn.addActionListener(e -> exportEntries());
        deleteBtn.addActionListener(e -> deleteEntry());
        closeBtn.addActionListener(e -> { saveEntry(); dispose(); });
        actions.add(streakLabel);
        actions.add(todayBtn);
        actions.add(saveBtn);
        actions.add(exportBtn);
        actions.add(deleteBtn);
        actions.add(closeBtn);
        topBar.add(actions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        dateList = new JList<>(listModel);
        ModernUI.styleList(dateList, theme);
        dateList.setFont(ModernUI.monoFont(Font.PLAIN, 13f));
        dateList.setFixedCellHeight(48);
        dateList.setCellRenderer(new DiaryEntryRenderer());
        dateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressListSelection) loadEntry(dateList.getSelectedValue());
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
        JPanel moodPanel = buildMoodPanel();
        countLabel = new JLabel();
        countLabel.setHorizontalAlignment(SwingConstants.LEFT);
        countLabel.setFont(ModernUI.monoFont(Font.PLAIN, 12f));
        countLabel.setForeground(ModernUI.mix(theme.getForeground(), theme.getAccent(), 0.14f));
        countLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));
        updateEntryCount();

        tagField = new JTextField();
        ModernUI.styleTextField(tagField, theme, false);
        installPlaceholder(tagField, TAG_PLACEHOLDER);
        JPanel tagPanel = ModernUI.transparentPanel(new BorderLayout(0, 6));
        tagPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        tagPanel.add(tagField, BorderLayout.NORTH);
        tagPanel.add(buildQuickTagPanel(), BorderLayout.SOUTH);

        JPanel editorFooter = new JPanel();
        editorFooter.setOpaque(false);
        editorFooter.setLayout(new BoxLayout(editorFooter, BoxLayout.Y_AXIS));
        editorFooter.add(countLabel);
        editorFooter.add(tagPanel);

        SurfacePanel listShell = new SurfacePanel(new BorderLayout(), ModernUI.panelColor(theme), ModernUI.hairline(theme), ModernUI.RADIUS);
        listShell.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        listShell.add(listScroll, BorderLayout.CENTER);

        SurfacePanel editShell = new SurfacePanel(new BorderLayout(), ModernUI.panelColor(theme), ModernUI.hairline(theme), ModernUI.RADIUS);
        editShell.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        editShell.add(moodPanel, BorderLayout.NORTH);
        editShell.add(editScroll, BorderLayout.CENTER);
        editShell.add(editorFooter, BorderLayout.SOUTH);

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

    private JPanel buildMoodPanel() {
        JPanel panel = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        for (String mood : MOODS) {
            JButton button = new JButton(mood);
            button.setFont(emojiFont(22f));
            button.setPreferredSize(new Dimension(42, 36));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> selectMood(mood));
            moodButtons.add(button);
            panel.add(button);
        }
        return panel;
    }

    private Font emojiFont(float size) {
        Set<String> installedFonts = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String[] candidates = {
            "Segoe UI Emoji",
            "Segoe UI Symbol",
            "Noto Color Emoji",
            "Apple Color Emoji"
        };
        for (String family : candidates) {
            if (installedFonts.contains(family)) {
                return new Font(family, Font.PLAIN, Math.round(size));
            }
        }
        return ModernUI.uiFont(Font.PLAIN, size);
    }

    private JPanel buildQuickTagPanel() {
        JPanel panel = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        for (String tag : QUICK_TAGS) {
            JButton chip = new JButton(tag);
            ModernUI.styleButton(chip, theme, "ghost");
            chip.setFont(ModernUI.monoFont(Font.BOLD, 11f));
            chip.addActionListener(e -> appendQuickTag(tag));
            panel.add(chip);
        }
        return panel;
    }

    private void selectMood(String mood) {
        selectedMood = mood;
        updateMoodButtons();
    }

    private void clearMood() {
        selectedMood = null;
        updateMoodButtons();
    }

    private void updateMoodButtons() {
        for (JButton button : moodButtons) {
            boolean selected = Objects.equals(button.getText(), selectedMood);
            button.setContentAreaFilled(selected);
            button.setOpaque(selected);
            button.setBackground(selected ? ModernUI.accentSoft(theme) : ModernUI.panelColor(theme));
        }
    }

    private void appendQuickTag(String tag) {
        String current = getFieldValue(tagField, TAG_PLACEHOLDER);
        Set<String> existing = parseTags(current);
        if (existing.contains(tag)) return;
        tagField.setForeground(theme.getForeground());
        tagField.setText(current.isEmpty() ? tag : current + " " + tag);
    }

    private void installPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(ModernUI.withAlpha(theme.getForeground(), 135));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(theme.getForeground());
                }
            }

            @Override public void focusLost(FocusEvent e) {
                if (field.getText().trim().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(ModernUI.withAlpha(theme.getForeground(), 135));
                }
            }
        });
    }

    private String getFieldValue(JTextField field, String placeholder) {
        if (field == null) return "";
        String text = field.getText();
        return text.equals(placeholder) ? "" : text.trim();
    }

    private void loadEntries() {
        refreshTagFilterOptions();
        applyEntryFilters();
        openToday();
    }

    private void applyEntryFilters() {
        if (listModel == null) return;

        String previouslySelected = dateList != null ? dateList.getSelectedValue() : currentDate;
        String search = getFieldValue(searchField, SEARCH_PLACEHOLDER).toLowerCase(Locale.ROOT);
        String selectedTag = getSelectedTagFilter();
        listModel.clear();
        tagPreviewCache.clear();

        File[] files = diaryDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
            for (File file : files) {
                String date = file.getName().replace(".txt", "");
                if (entryMatchesFilters(date, search, selectedTag)) {
                    listModel.addElement(date);
                    cacheTagsForDate(date);
                }
            }
        }

        if (search.isEmpty() && selectedTag == null) {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (!listModel.contains(today)) {
                listModel.add(0, today);
                cacheTagsForDate(today);
            }
        }

        if (previouslySelected != null && listModel.contains(previouslySelected)) {
            suppressListSelection = true;
            try {
                dateList.setSelectedValue(previouslySelected, true);
            } finally {
                suppressListSelection = false;
            }
        }
    }

    private boolean entryMatchesFilters(String date, String search, String selectedTag) {
        if (selectedTag != null && !readTags(date).contains(selectedTag)) {
            return false;
        }
        if (!search.isEmpty()) {
            File entryFile = new File(diaryDir, date + ".txt");
            if (!entryFile.exists()) return false;
            try {
                return readEntryText(entryFile).toLowerCase(Locale.ROOT).contains(search);
            } catch (IOException ex) {
                return false;
            }
        }
        return true;
    }

    private String getSelectedTagFilter() {
        if (tagFilterBox == null || tagFilterBox.getSelectedIndex() <= 0) return null;
        Object selected = tagFilterBox.getSelectedItem();
        return selected == null ? null : selected.toString();
    }

    private void refreshTagFilterOptions() {
        if (tagFilterBox == null) return;

        String previous = getSelectedTagFilter();
        TreeSet<String> tags = new TreeSet<>();
        File[] tagFiles = diaryDir.listFiles((dir, name) -> name.endsWith(".tags"));
        if (tagFiles != null) {
            for (File file : tagFiles) {
                tags.addAll(readTagFile(file));
            }
        }

        updatingTagFilter = true;
        tagFilterBox.removeAllItems();
        tagFilterBox.addItem("All");
        for (String tag : tags) tagFilterBox.addItem(tag);
        tagFilterBox.setSelectedItem(previous != null && tags.contains(previous) ? previous : "All");
        updatingTagFilter = false;
    }

    private void cacheTagsForDate(String date) {
        java.util.List<String> tags = readTags(date);
        tagPreviewCache.put(date, String.join(" ", tags));
    }

    private void openToday() {
        currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (!listModel.contains(currentDate)) {
            listModel.add(0, currentDate);
            cacheTagsForDate(currentDate);
        }
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
        loadMood(date);
        loadTagsIntoField(date);
    }

    private void saveEntry() {
        if (currentDate == null) return;
        File entryFile = new File(diaryDir, currentDate + ".txt");
        try {
            Files.write(entryFile.toPath(), encryptEntry(entryArea.getText()));
            saveMood(currentDate);
            saveTags(currentDate);
            refreshTagFilterOptions();
            applyEntryFilters();
            if (listModel.contains(currentDate)) dateList.setSelectedValue(currentDate, true);
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
            new File(diaryDir, currentDate + ".mood").delete();
            new File(diaryDir, currentDate + ".tags").delete();
            loadEntries();
        }
    }

    private void saveMood(String date) throws IOException {
        File moodFile = new File(diaryDir, date + ".mood");
        if (selectedMood == null || selectedMood.isBlank()) {
            Files.deleteIfExists(moodFile.toPath());
            return;
        }
        Files.writeString(moodFile.toPath(), selectedMood, StandardCharsets.UTF_8);
    }

    private void loadMood(String date) {
        File moodFile = new File(diaryDir, date + ".mood");
        if (!moodFile.exists()) {
            clearMood();
            return;
        }
        try {
            String mood = Files.readString(moodFile.toPath(), StandardCharsets.UTF_8).trim();
            for (String knownMood : MOODS) {
                if (knownMood.equals(mood)) {
                    selectMood(mood);
                    return;
                }
            }
        } catch (IOException ex) {
            // Treat unreadable mood metadata like no mood.
        }
        clearMood();
    }

    private void saveTags(String date) throws IOException {
        File tagFile = new File(diaryDir, date + ".tags");
        Set<String> tags = parseTags(getFieldValue(tagField, TAG_PLACEHOLDER));
        if (tags.isEmpty()) {
            Files.deleteIfExists(tagFile.toPath());
            return;
        }
        Files.write(tagFile.toPath(), tags, StandardCharsets.UTF_8);
        tagPreviewCache.put(date, String.join(" ", tags));
    }

    private void loadTagsIntoField(String date) {
        java.util.List<String> tags = readTags(date);
        if (tags.isEmpty()) {
            tagField.setText(TAG_PLACEHOLDER);
            tagField.setForeground(ModernUI.withAlpha(theme.getForeground(), 135));
        } else {
            tagField.setText(String.join(" ", tags));
            tagField.setForeground(theme.getForeground());
        }
    }

    private java.util.List<String> readTags(String date) {
        return readTagFile(new File(diaryDir, date + ".tags"));
    }

    private java.util.List<String> readTagFile(File file) {
        if (!file.exists()) return Collections.emptyList();
        try {
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String tag = line.trim().toLowerCase(Locale.ROOT);
                if (tag.startsWith("#") && tag.length() > 1) tags.add(tag);
            }
            return new ArrayList<>(tags);
        } catch (IOException ex) {
            return Collections.emptyList();
        }
    }

    private Set<String> parseTags(String text) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String token : text.trim().split("\\s+")) {
            String tag = token.trim().toLowerCase(Locale.ROOT);
            if (tag.startsWith("#") && tag.length() > 1) tags.add(tag);
        }
        return tags;
    }

    private void exportEntries() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Diary Entries");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File targetDir = chooser.getSelectedFile();
        int exported = 0;
        File[] files = diaryDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                try {
                    String text = readEntryTextForExport(file);
                    Files.writeString(new File(targetDir, file.getName()).toPath(), text, StandardCharsets.UTF_8);
                    exported++;
                } catch (Exception ex) {
                    // Skip unreadable or undecryptable entries during export.
                }
            }
        }
        JOptionPane.showMessageDialog(this, "Exported " + exported + " entries to " + targetDir.getAbsolutePath());
    }

    private String readEntryTextForExport(File entryFile) throws Exception {
        byte[] fileBytes = Files.readAllBytes(entryFile.toPath());
        String stored = new String(fileBytes, StandardCharsets.UTF_8);
        if (stored.startsWith(ENCRYPTED_ENTRY_HEADER)) {
            return decryptEntry(fileBytes);
        }
        return Files.readString(entryFile.toPath(), StandardCharsets.UTF_8);
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

    private class DiaryEntryRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel dateLabel = new JLabel();
        private final JLabel tagsLabel = new JLabel();

        DiaryEntryRenderer() {
            setLayout(new BorderLayout(0, 2));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            dateLabel.setFont(ModernUI.monoFont(Font.PLAIN, 13f));
            tagsLabel.setFont(ModernUI.uiFont(Font.PLAIN, 11f));
            add(dateLabel, BorderLayout.NORTH);
            add(tagsLabel, BorderLayout.CENTER);
        }

        public Component getListCellRendererComponent(
            JList<? extends String> list,
            String value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

            String tags = tagPreviewCache.computeIfAbsent(value, date -> String.join(" ", readTags(date)));
            dateLabel.setText(value);
            tagsLabel.setText(tags.isEmpty() ? " " : tags);

            Color background = isSelected ? ModernUI.accentSoft(theme) : ModernUI.panelColor(theme);
            Color foreground = theme.getForeground();
            setOpaque(true);
            setBackground(background);
            dateLabel.setForeground(foreground);
            tagsLabel.setForeground(ModernUI.withAlpha(foreground, isSelected ? 175 : 120));
            return this;
        }
    }
}
