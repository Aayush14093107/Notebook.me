import java.awt.*;
import javax.swing.*;

class TypingTestDialog extends JDialog {
    private static final String[][] SAMPLE_TEXTS = {
        {
            "The quick brown fox jumps over the lazy dog. Programming is the art of telling another human what one wants the computer to do. Every great developer you know got there by solving problems they were unqualified to solve until they actually did it.",
            "In the middle of difficulty lies opportunity. The only way to do great work is to love what you do. Innovation distinguishes between a leader and a follower. Stay hungry, stay foolish, and never stop learning new things every single day.",
            "Careful work often looks quiet from the outside. A clean notebook, a few honest questions, and one patient revision can turn a rough idea into something sturdy enough to share.",
            "Morning light settled across the desk while the city gathered its noise. She opened the file, found the hidden bug, and smiled at the small kindness of a test that failed clearly.",
            "Good tools disappear into the rhythm of the task. They leave just enough friction to keep your attention awake and just enough comfort to help you keep going."
        },
        {
            "the quick brown fox jumps over the lazy dog programming is the art of telling another human what one wants the computer to do every great developer you know got there by solving problems they were unqualified to solve",
            "in the middle of difficulty lies opportunity the only way to do great work is to love what you do innovation distinguishes between a leader and a follower stay hungry stay foolish and never stop learning",
            "steady practice turns awkward keys into familiar paths each sentence gives your hands a little more confidence and each mistake shows exactly where to slow down",
            "clear notes save time later because memory is generous in the moment and surprisingly vague when the deadline returns",
            "small steps compound over long afternoons until the work that once felt heavy begins to move with surprising ease"
        },
        {
            "The server has 128 GB of RAM and 24 CPU cores running at 3.6 GHz. In 2024 over 500 million users accessed the platform. The total cost was $49.99 per month for 12 months totaling $599.88 annually.",
            "Order 4521 contains 3 items weighing 2.5 kg each for a total of 7.5 kg. The package dimensions are 40 x 30 x 20 cm. Shipping to zone 7 costs $15.75 with a 2 to 5 business day delivery window.",
            "At 9:15 AM the backup copied 42 files in 18 seconds, then verified 42 checksums with 0 errors. The final archive measured 73.4 MB.",
            "Recipe version 3 uses 250 g of flour, 125 g of butter, and 80 g of sugar. Bake at 180 C for 22 minutes before cooling for 10 minutes."
        }
    };

    private final Theme theme;
    private JTextArea sampleArea;
    private JTextArea typingArea;
    private JLabel timerLabel;
    private JLabel resultLabel;
    private JLabel statsLabel;
    private JButton restartButton;
    private int durationSecs;
    private int timeLeft;
    private javax.swing.Timer countdownTimer;
    private boolean testStarted = false;
    private boolean testFinished = false;
    private int currentMode = 0;
    private String sampleText;

    public TypingTestDialog(JFrame parent, Theme theme) {
        super(parent, "Typing Speed Test", true);
        this.theme = theme;
        this.sampleText = pickSample();
        setSize(780, 620);
        setLocationRelativeTo(parent);
        buildUI();
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

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Typing speed test");
        title.setFont(ModernUI.uiFont(Font.BOLD, 18f));
        title.setForeground(theme.getForeground());
        titleStack.add(title);
        header.add(titleStack, BorderLayout.WEST);

        JPanel controls = ModernUI.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JLabel modeLabel = new JLabel("Mode");
        modeLabel.setForeground(theme.getForeground());
        modeLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        JComboBox<String> modeBox = new JComboBox<>(new String[]{"Normal", "No Punctuation", "With Numbers"});
        ModernUI.styleComboBox(modeBox, theme);
        modeBox.addActionListener(e -> {
            currentMode = modeBox.getSelectedIndex();
            sampleText = pickSample();
            resetTestForModeChange();
        });
        timerLabel = new JLabel("Pick a duration");
        timerLabel.setFont(ModernUI.monoFont(Font.BOLD, 18f));
        timerLabel.setForeground(theme.getForeground());
        controls.add(modeLabel);
        controls.add(modeBox);
        controls.add(Box.createHorizontalStrut(8));
        restartButton = new JButton("Restart (same text)");
        ModernUI.styleButton(restartButton, theme, "secondary");
        restartButton.setEnabled(false);
        restartButton.addActionListener(e -> restartSameText());
        controls.add(restartButton);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(durationButton("30s", 30));
        controls.add(durationButton("60s", 60));
        controls.add(durationButton("5m", 300));
        controls.add(Box.createHorizontalStrut(12));
        controls.add(timerLabel);
        header.add(controls, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        sampleArea = area(sampleText, true);
        typingArea = area("", false);
        typingArea.setEnabled(false);
        typingArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { enableRestartIfTyping(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { enableRestartIfTyping(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { enableRestartIfTyping(); }
        });

        SurfacePanel sampleShell = surfaceCard("Text to type", sampleArea);
        SurfacePanel typingShell = surfaceCard("Start typing", typingArea);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 8));
        center.setOpaque(false);
        center.add(sampleShell);
        center.add(typingShell);
        root.add(center, BorderLayout.CENTER);

        SurfacePanel footer = new SurfacePanel(
            new GridLayout(2, 1, 0, 4),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        resultLabel = new JLabel("Ready when you are", JLabel.CENTER);
        resultLabel.setFont(ModernUI.monoFont(Font.BOLD, 13f));
        resultLabel.setForeground(ModernUI.mix(theme.getForeground(), theme.getAccent(), 0.18f));
        statsLabel = new JLabel(" ", JLabel.CENTER);
        statsLabel.setFont(ModernUI.monoFont(Font.PLAIN, 12f));
        statsLabel.setForeground(theme.getForeground());
        footer.add(resultLabel);
        footer.add(statsLabel);
        root.add(footer, BorderLayout.SOUTH);
    }

    private JTextArea area(String text, boolean locked) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(!locked);
        area.setFont(locked ? new Font("Georgia", Font.ITALIC, 14) : ModernUI.monoFont(Font.PLAIN, 14f));
        area.setBackground(ModernUI.editorColor(theme));
        area.setForeground(theme.getForeground());
        area.setCaretColor(theme.getAccent());
        area.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return area;
    }

    private SurfacePanel surfaceCard(String title, JTextArea area) {
        SurfacePanel shell = new SurfacePanel(
            new BorderLayout(0, 10),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        shell.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel label = new JLabel(title);
        label.setFont(ModernUI.uiFont(Font.BOLD, 12f));
        label.setForeground(ModernUI.mix(theme.getForeground(), theme.getAccent(), 0.14f));
        JScrollPane scroll = new JScrollPane(area);
        ModernUI.styleScrollPane(scroll, theme, ModernUI.editorColor(theme));
        shell.add(label, BorderLayout.NORTH);
        shell.add(scroll, BorderLayout.CENTER);
        return shell;
    }

    private JButton durationButton(String text, int seconds) {
        JButton button = new JButton(text);
        ModernUI.styleButton(button, theme, "secondary");
        button.addActionListener(e -> startTest(seconds));
        return button;
    }

    private String pickSample() {
        String[] pool = SAMPLE_TEXTS[currentMode];
        return pool[(int) (Math.random() * pool.length)];
    }

    private void startTest(int seconds) {
        if (testStarted && !testFinished) return;
        durationSecs = seconds;
        timeLeft = seconds;
        testStarted = true;
        testFinished = false;
        typingArea.setText("");
        typingArea.setEnabled(true);
        typingArea.requestFocus();
        resultLabel.setText(" ");
        statsLabel.setText(" ");
        timerLabel.setText(formatTime(timeLeft));
        sampleText = pickSample();
        sampleArea.setText(sampleText);
        restartButton.setEnabled(false);

        startCountdownTimer();
    }

    private void restartSameText() {
        if (!testStarted) return;

        if (countdownTimer != null) countdownTimer.stop();
        timeLeft = durationSecs;
        testStarted = true;
        testFinished = false;
        typingArea.setText("");
        typingArea.setEnabled(true);
        typingArea.requestFocus();
        resultLabel.setText(" ");
        statsLabel.setText(" ");
        timerLabel.setText(formatTime(timeLeft));
        restartButton.setEnabled(false);
        startCountdownTimer();
    }

    private void resetTestForModeChange() {
        if (countdownTimer != null) countdownTimer.stop();
        testStarted = false;
        testFinished = false;
        durationSecs = 0;
        timeLeft = 0;

        if (sampleArea != null) sampleArea.setText(sampleText);
        if (typingArea != null) {
            typingArea.setText("");
            typingArea.setEnabled(false);
        }
        if (resultLabel != null) resultLabel.setText("Ready when you are");
        if (statsLabel != null) statsLabel.setText(" ");
        if (timerLabel != null) timerLabel.setText("Pick a duration");
        if (restartButton != null) restartButton.setEnabled(false);
    }

    private void startCountdownTimer() {
        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText(formatTime(timeLeft));
            if (timeLeft <= 0) {
                ((javax.swing.Timer) e.getSource()).stop();
                endTest();
            }
        });
        countdownTimer.start();
    }

    private void enableRestartIfTyping() {
        if (restartButton != null && testStarted && !testFinished && typingArea.getDocument().getLength() > 0) {
            restartButton.setEnabled(true);
        }
    }

    private void endTest() {
        testFinished = true;
        typingArea.setEnabled(false);
        String typed = typingArea.getText().trim();
        String[] sampleWords = sampleText.trim().split("\\s+");
        String[] typedWords = typed.isEmpty() ? new String[0] : typed.split("\\s+");

        int correctWords = 0;
        int errorWords = 0;
        int longestStreak = 0;
        int currentStreak = 0;
        int totalChars = typed.length();
        for (int i = 0; i < Math.min(typedWords.length, sampleWords.length); i++) {
            if (typedWords[i].equals(sampleWords[i])) {
                correctWords++;
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                errorWords++;
                currentStreak = 0;
            }
        }

        double minutes = durationSecs / 60.0;
        int wpm = (int) (correctWords / minutes);
        int cpm = (int) (totalChars / minutes);
        double accuracy = typedWords.length > 0 ? (correctWords * 100.0 / typedWords.length) : 0;

        resultLabel.setText(String.format("WPM %d | Accuracy %.1f%% | Correct %d/%d", wpm, accuracy, correctWords, typedWords.length));
        statsLabel.setText(String.format("CPM %d | Errors %d | Longest streak %d | Mode %s", cpm, errorWords, longestStreak,
            currentMode == 0 ? "Normal" : currentMode == 1 ? "No Punctuation" : "Numbers"));
        timerLabel.setText("Done");
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
