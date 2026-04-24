import java.awt.*;
import javax.swing.*;

// ════════════════════════════════════════════════════════
//  Typing Speed Test Dialog — v4.0 with modes
// ════════════════════════════════════════════════════════
class TypingTestDialog extends JDialog {
    private static final String[][] SAMPLE_TEXTS = {
        // Normal
        { "The quick brown fox jumps over the lazy dog. Programming is the art of telling another human what one wants the computer to do. Every great developer you know got there by solving problems they were unqualified to solve until they actually did it.",
          "In the middle of difficulty lies opportunity. The only way to do great work is to love what you do. Innovation distinguishes between a leader and a follower. Stay hungry, stay foolish, and never stop learning new things every single day." },
        // No Punctuation
        { "the quick brown fox jumps over the lazy dog programming is the art of telling another human what one wants the computer to do every great developer you know got there by solving problems they were unqualified to solve",
          "in the middle of difficulty lies opportunity the only way to do great work is to love what you do innovation distinguishes between a leader and a follower stay hungry stay foolish and never stop learning" },
        // With Numbers
        { "The server has 128 GB of RAM and 24 CPU cores running at 3.6 GHz. In 2024 over 500 million users accessed the platform. The total cost was $49.99 per month for 12 months totaling $599.88 annually.",
          "Order 4521 contains 3 items weighing 2.5 kg each for a total of 7.5 kg. The package dimensions are 40 x 30 x 20 cm. Shipping to zone 7 costs $15.75 with a 2 to 5 business day delivery window." }
    };

    private JTextArea sampleArea;
    private JTextArea typingArea;
    private JLabel timerLabel;
    private JLabel resultLabel;
    private JLabel statsLabel;
    private int durationSecs;
    private int timeLeft;
    private javax.swing.Timer countdownTimer;
    private boolean testStarted = false;
    private boolean testFinished = false;
    private int currentMode = 0; // 0=Normal, 1=No Punctuation, 2=Numbers
    private String sampleText;

    public TypingTestDialog(JFrame parent, Theme theme) {
        super(parent, "Typing Speed Test", true);
        setSize(750, 580);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(theme.getBackground());

        sampleText = pickSample();

        // ── Top panel: mode + duration ──
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        topPanel.setBackground(theme.getMenuBg());
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.getBorder()));

        // Mode selector
        JLabel modeLbl = new JLabel("Mode:"); modeLbl.setForeground(theme.getForeground()); modeLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String[] modes = {"Normal", "No Punctuation", "With Numbers"};
        JComboBox<String> modeBox = new JComboBox<>(modes);
        modeBox.setSelectedIndex(0);
        modeBox.addActionListener(e -> { currentMode = modeBox.getSelectedIndex(); sampleText = pickSample(); sampleArea.setText(sampleText); });
        topPanel.add(modeLbl); topPanel.add(modeBox);

        topPanel.add(Box.createHorizontalStrut(16));

        // Duration buttons
        JLabel durLbl = new JLabel("Duration:"); durLbl.setForeground(theme.getForeground()); durLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topPanel.add(durLbl);
        int[] durations = {30, 60, 300};
        String[] labels = {"30s", "60s", "5m"};
        for (int i = 0; i < durations.length; i++) {
            JButton btn = new JButton(labels[i]);
            btn.setBackground(theme.getAccent());
            btn.setForeground(new Color(15, 17, 21));
            btn.setFocusPainted(false); btn.setBorderPainted(false);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final int dur = durations[i];
            btn.addActionListener(e -> startTest(dur));
            topPanel.add(btn);
        }

        timerLabel = new JLabel("Pick mode & duration", JLabel.CENTER);
        timerLabel.setForeground(theme.getAccent());
        timerLabel.setFont(new Font("Consolas", Font.BOLD, 20));
        topPanel.add(Box.createHorizontalStrut(12));
        topPanel.add(timerLabel);

        // ── Sample text ──
        sampleArea = new JTextArea(sampleText);
        sampleArea.setLineWrap(true); sampleArea.setWrapStyleWord(true); sampleArea.setEditable(false);
        sampleArea.setFont(new Font("Georgia", Font.ITALIC, 14));
        sampleArea.setBackground(theme.getSecondary()); sampleArea.setForeground(theme.getForeground());
        sampleArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(theme.getBorder()),
                " Text to type: ", 0, 0, new Font("Segoe UI", Font.PLAIN, 11), theme.getForeground()),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        // ── Typing area ──
        typingArea = new JTextArea();
        typingArea.setLineWrap(true); typingArea.setWrapStyleWord(true);
        typingArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        typingArea.setBackground(theme.getBackground()); typingArea.setForeground(theme.getForeground());
        typingArea.setCaretColor(theme.getAccent());
        typingArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(theme.getBorder()),
                " Start typing: ", 0, 0, new Font("Segoe UI", Font.PLAIN, 11), theme.getForeground()),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        typingArea.setEnabled(false);

        // ── Results ──
        resultLabel = new JLabel(" ", JLabel.CENTER);
        resultLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        resultLabel.setForeground(theme.getAccent());
        statsLabel = new JLabel(" ", JLabel.CENTER);
        statsLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
        statsLabel.setForeground(theme.getForeground());

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 6));
        centerPanel.setBackground(theme.getBackground());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        centerPanel.add(new JScrollPane(sampleArea)); centerPanel.add(new JScrollPane(typingArea));

        JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
        bottomPanel.setBackground(theme.getStatusBg());
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.getBorder()));
        bottomPanel.add(resultLabel); bottomPanel.add(statsLabel);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private String pickSample() {
        String[] pool = SAMPLE_TEXTS[currentMode];
        return pool[(int)(Math.random() * pool.length)];
    }

    private void startTest(int seconds) {
        if (testStarted && !testFinished) return;
        durationSecs = seconds; timeLeft = seconds;
        testStarted = true; testFinished = false;
        typingArea.setText(""); typingArea.setEnabled(true); typingArea.requestFocus();
        resultLabel.setText(" "); statsLabel.setText(" ");
        timerLabel.setText(formatTime(timeLeft));
        sampleText = pickSample(); sampleArea.setText(sampleText);

        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText(formatTime(timeLeft));
            if (timeLeft <= 0) { ((javax.swing.Timer) e.getSource()).stop(); endTest(); }
        });
        countdownTimer.start();
    }

    private void endTest() {
        testFinished = true; typingArea.setEnabled(false);
        String typed = typingArea.getText().trim();
        String[] sampleWords = sampleText.trim().split("\\s+");
        String[] typedWords = typed.isEmpty() ? new String[0] : typed.split("\\s+");

        int correctWords = 0, errorWords = 0, longestStreak = 0, currentStreak = 0;
        int totalChars = typed.length();
        for (int i = 0; i < Math.min(typedWords.length, sampleWords.length); i++) {
            if (typedWords[i].equals(sampleWords[i])) { correctWords++; currentStreak++; longestStreak = Math.max(longestStreak, currentStreak); }
            else { errorWords++; currentStreak = 0; }
        }

        double minutes = durationSecs / 60.0;
        int wpm = (int)(correctWords / minutes);
        int cpm = (int)(totalChars / minutes);
        double accuracy = typedWords.length > 0 ? (correctWords * 100.0 / typedWords.length) : 0;

        resultLabel.setText(String.format("WPM: %d  |  Accuracy: %.1f%%  |  Correct: %d/%d words", wpm, accuracy, correctWords, typedWords.length));
        statsLabel.setText(String.format("CPM: %d  |  Errors: %d  |  Longest Streak: %d words  |  Mode: %s", cpm, errorWords, longestStreak,
            currentMode == 0 ? "Normal" : currentMode == 1 ? "No Punctuation" : "Numbers"));
        timerLabel.setText("Done!");
    }

    private String formatTime(int secs) { return String.format("%d:%02d", secs / 60, secs % 60); }
}
