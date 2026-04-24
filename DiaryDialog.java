import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;

// ════════════════════════════════════════════════════════
//  Diary Mode — Password-protected journaling
// ════════════════════════════════════════════════════════
class DiaryDialog extends JDialog {
    private File diaryDir;
    private JList<String> dateList;
    private DefaultListModel<String> listModel;
    private JTextArea entryArea;
    private String currentDate;
    private Theme theme;

    public DiaryDialog(JFrame parent, Theme theme, File notebookDir) {
        super(parent, "Diary Mode", true);
        this.theme = theme;
        this.diaryDir = new File(notebookDir, "diary");
        if (!diaryDir.exists()) diaryDir.mkdirs();

        if (!authenticate()) { dispose(); return; }

        setSize(750, 520);
        setLocationRelativeTo(parent);
        buildUI();
        loadEntries();
    }

    private static final String[] SECURITY_QUESTIONS = {
        "What is your pet's name?",
        "What city were you born in?",
        "What is your favorite book?",
        "What was your first school?",
        "What is your mother's maiden name?"
    };

    private boolean authenticate() {
        File pinFile = new File(diaryDir, ".pin");
        File sqFile = new File(diaryDir, ".security");
        if (!pinFile.exists()) {
            // First time — set PIN + security question
            JPasswordField pf1 = new JPasswordField(10);
            JPasswordField pf2 = new JPasswordField(10);
            JComboBox<String> questionBox = new JComboBox<>(SECURITY_QUESTIONS);
            JTextField answerField = new JTextField(15);

            JPanel panel = new JPanel(new GridLayout(6, 1, 4, 4));
            panel.add(new JLabel("Create a PIN for your diary:"));
            panel.add(pf1);
            panel.add(pf2);
            panel.add(new JLabel("Security question (for PIN reset):"));
            panel.add(questionBox);
            panel.add(answerField);

            int r = JOptionPane.showConfirmDialog(null, panel, "Set Diary PIN", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return false;
            String p1 = new String(pf1.getPassword());
            String p2 = new String(pf2.getPassword());
            String answer = answerField.getText().trim();
            if (p1.isEmpty() || !p1.equals(p2)) {
                JOptionPane.showMessageDialog(null, "PINs don't match or empty.");
                return false;
            }
            if (answer.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Please provide a security answer.");
                return false;
            }
            try {
                Files.writeString(pinFile.toPath(), hashPin(p1));
                // Store question index + hashed answer
                int qIdx = questionBox.getSelectedIndex();
                Files.writeString(sqFile.toPath(), qIdx + "\n" + hashPin(answer.toLowerCase()));
            } catch (IOException e) { return false; }
            return true;
        } else {
            // Verify PIN — with Forgot PIN option
            JPasswordField pf = new JPasswordField(10);
            JPanel panel = new JPanel(new BorderLayout(4, 8));
            JPanel fields = new JPanel(new GridLayout(2, 1, 4, 4));
            fields.add(new JLabel("Enter your diary PIN:"));
            fields.add(pf);
            panel.add(fields, BorderLayout.CENTER);

            JButton forgotBtn = new JButton("Forgot PIN?");
            forgotBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            forgotBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            forgotBtn.setForeground(new Color(70, 130, 220));
            forgotBtn.setContentAreaFilled(false);
            forgotBtn.setBorderPainted(false);
            panel.add(forgotBtn, BorderLayout.SOUTH);

            // Forgot PIN handler
            forgotBtn.addActionListener(e -> {
                // Close the PIN dialog
                Window w = SwingUtilities.getWindowAncestor(forgotBtn);
                if (w != null) w.dispose();
            });

            int r = JOptionPane.showConfirmDialog(null, panel, "Diary PIN", JOptionPane.OK_CANCEL_OPTION);

            // Check if Forgot PIN was clicked (dialog was disposed, r will be CLOSED_OPTION)
            if (r == JOptionPane.CLOSED_OPTION) {
                return handleForgotPIN(pinFile, sqFile);
            }
            if (r != JOptionPane.OK_OPTION) return false;

            try {
                String stored = Files.readString(pinFile.toPath()).trim();
                String entered = hashPin(new String(pf.getPassword()));
                if (!stored.equals(entered)) {
                    JOptionPane.showMessageDialog(null, "Wrong PIN!", "Access Denied", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                return true;
            } catch (IOException e) { return false; }
        }
    }

    private boolean handleForgotPIN(File pinFile, File sqFile) {
        if (!sqFile.exists()) {
            JOptionPane.showMessageDialog(null, "No security question set. Cannot reset PIN.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        try {
            String[] sqData = Files.readString(sqFile.toPath()).trim().split("\n");
            int qIdx = Integer.parseInt(sqData[0]);
            String storedHash = sqData[1].trim();
            String question = SECURITY_QUESTIONS[qIdx];

            JTextField answerField = new JTextField(15);
            JPanel panel = new JPanel(new GridLayout(3, 1, 4, 4));
            panel.add(new JLabel("Security Question:"));
            panel.add(new JLabel(question));
            panel.add(answerField);

            int r = JOptionPane.showConfirmDialog(null, panel, "Reset PIN", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return false;

            String givenHash = hashPin(answerField.getText().trim().toLowerCase());
            if (!givenHash.equals(storedHash)) {
                JOptionPane.showMessageDialog(null, "Wrong answer!", "Access Denied", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            // Answer correct — set new PIN
            JPasswordField np1 = new JPasswordField(10);
            JPasswordField np2 = new JPasswordField(10);
            JPanel newPinPanel = new JPanel(new GridLayout(3, 1, 4, 4));
            newPinPanel.add(new JLabel("Set a new PIN:"));
            newPinPanel.add(np1);
            newPinPanel.add(np2);
            int r2 = JOptionPane.showConfirmDialog(null, newPinPanel, "New PIN", JOptionPane.OK_CANCEL_OPTION);
            if (r2 != JOptionPane.OK_OPTION) return false;
            String newP1 = new String(np1.getPassword());
            String newP2 = new String(np2.getPassword());
            if (newP1.isEmpty() || !newP1.equals(newP2)) {
                JOptionPane.showMessageDialog(null, "PINs don't match or empty.");
                return false;
            }
            Files.writeString(pinFile.toPath(), hashPin(newP1));
            JOptionPane.showMessageDialog(null, "PIN has been reset successfully!");
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to reset PIN.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pin.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return pin; }
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(theme.getBackground());

        // ── Left: date list ──
        listModel = new DefaultListModel<>();
        dateList = new JList<>(listModel);
        dateList.setBackground(theme.getMenuBg());
        dateList.setForeground(theme.getForeground());
        dateList.setSelectionBackground(theme.getAccent());
        dateList.setSelectionForeground(theme.getBackground());
        dateList.setFont(new Font("Consolas", Font.PLAIN, 13));
        dateList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        dateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadEntry(dateList.getSelectedValue());
        });

        JScrollPane listScroll = new JScrollPane(dateList);
        listScroll.setPreferredSize(new Dimension(160, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.getBorder()));

        // ── Right: entry editor ──
        entryArea = new JTextArea();
        entryArea.setLineWrap(true);
        entryArea.setWrapStyleWord(true);
        entryArea.setFont(new Font("Georgia", Font.PLAIN, 14));
        entryArea.setBackground(theme.getBackground());
        entryArea.setForeground(theme.getForeground());
        entryArea.setCaretColor(theme.getAccent());
        entryArea.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JScrollPane editScroll = new JScrollPane(entryArea);
        editScroll.setBorder(BorderFactory.createEmptyBorder());

        // ── Top bar ──
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topBar.setBackground(theme.getMenuBg());
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.getBorder()));

        JLabel titleLbl = new JLabel("  My Diary");
        titleLbl.setFont(new Font("Georgia", Font.ITALIC, 15));
        titleLbl.setForeground(theme.getAccent());

        JLabel streakLbl = new JLabel(calculateStreak());
        streakLbl.setFont(new Font("Segoe UI Emoji", Font.BOLD, 12));
        streakLbl.setForeground(new Color(255, 140, 50));

        JButton todayBtn = makeBtn("Today's Entry");
        todayBtn.addActionListener(e -> openToday());

        JButton saveBtn = makeBtn("Save");
        saveBtn.setBackground(theme.getAccent());
        saveBtn.setForeground(new Color(15, 17, 21));
        saveBtn.addActionListener(e -> { saveEntry(); streakLbl.setText(calculateStreak()); });

        JButton deleteBtn = makeBtn("Delete Entry");
        deleteBtn.addActionListener(e -> deleteEntry());

        JButton closeBtn = makeBtn("Close");
        closeBtn.addActionListener(e -> { saveEntry(); dispose(); });

        topBar.add(titleLbl);
        topBar.add(streakLbl);
        topBar.add(Box.createHorizontalStrut(12));
        topBar.add(todayBtn);
        topBar.add(saveBtn);
        topBar.add(deleteBtn);
        topBar.add(closeBtn);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, editScroll);
        split.setDividerLocation(160);
        split.setDividerSize(3);
        split.setBorder(null);

        add(topBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private JButton makeBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(theme.getSecondary());
        b.setForeground(theme.getForeground());
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void loadEntries() {
        listModel.clear();
        File[] files = diaryDir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName())); // newest first
            for (File f : files) {
                listModel.addElement(f.getName().replace(".txt", ""));
            }
        }
        openToday();
    }

    private void openToday() {
        currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        // Check if already in list
        if (!listModel.contains(currentDate)) {
            listModel.add(0, currentDate);
        }
        dateList.setSelectedValue(currentDate, true);
        loadEntry(currentDate);
    }

    private void loadEntry(String date) {
        if (date == null) return;
        currentDate = date;
        File f = new File(diaryDir, date + ".txt");
        if (f.exists()) {
            try { entryArea.setText(Files.readString(f.toPath())); entryArea.setCaretPosition(0); }
            catch (IOException e) { entryArea.setText(""); }
        } else {
            entryArea.setText("Dear Diary,\n\n");
            entryArea.setCaretPosition(entryArea.getText().length());
        }
    }

    private void saveEntry() {
        if (currentDate == null) return;
        File f = new File(diaryDir, currentDate + ".txt");
        try { Files.writeString(f.toPath(), entryArea.getText()); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, "Failed to save entry."); }
    }

    private void deleteEntry() {
        if (currentDate == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Delete entry for " + currentDate + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            new File(diaryDir, currentDate + ".txt").delete();
            loadEntries();
        }
    }

    private String calculateStreak() {
        int streak = 0;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // Check backwards from today
        for (int i = 0; i < 365; i++) {
            String dateStr = sdf.format(cal.getTime());
            File f = new File(diaryDir, dateStr + ".txt");
            if (f.exists() && f.length() > 0) {
                streak++;
            } else if (i > 0) {
                // Allow today to not have entry yet (i==0), but break on other gaps
                break;
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        if (streak == 0) return "  Start your streak today!";
        return "  \ud83d\udd25 " + streak + "-day streak!";
    }
}
