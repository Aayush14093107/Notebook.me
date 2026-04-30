import java.awt.*;
import javax.swing.*;

// ════════════════════════════════════════════════════════
//  Dyslexia-Friendly Mode - Accessibility settings
// ════════════════════════════════════════════════════════
class DyslexiaMode {
    boolean enabled = false;
    float letterSpacing = 0.15f;   // extra tracking
    float wordSpacing = 0.3f;     // extra word gap
    int maxLineWidth = 65;        // max characters per line
    float lineHeight = 2.0f;      // line height multiplier
    boolean readingRuler = false;  // colored line at cursor
    boolean lineFocus = false;    // dim all except current line
    Color bgColor = new Color(255, 251, 240);       // cream
    Color textColor = new Color(60, 55, 50);         // charcoal
    Color rulerColor = new Color(255, 220, 100, 60); // translucent yellow

    /** Get the dyslexia-optimized font (bold sans-serif, larger) */
    Font getFont(int baseSize) {
        int size = Math.max(baseSize, 16); // minimum 16pt
        return new Font("Verdana", Font.BOLD, size);
    }

    /** Apply dyslexia settings to a text area */
    void applyTo(JTextArea ta, int baseSize) {
        if (!enabled) return;
        ta.setFont(getFont(baseSize));
        ta.setBackground(bgColor);
        ta.setForeground(textColor);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        // Increase margins for shorter line width feel
        ta.setBorder(BorderFactory.createEmptyBorder(16, 40, 16, 40));
        ta.setCaretColor(new Color(120, 80, 40));
    }

    /** Creates the reading ruler overlay panel */
    JPanel createRulerOverlay() {
        return new JPanel() {
            private int rulerY = 0;
            {
                setOpaque(false);
                // Timer to track cursor position
            }
            public void setRulerY(int y) { this.rulerY = y; repaint(); }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!enabled || !readingRuler) return;
                Graphics2D g2 = (Graphics2D) g;
                // Reading ruler - translucent highlight bar
                g2.setColor(rulerColor);
                g2.fillRect(0, rulerY - 2, getWidth(), 28);
                // Thin guide line
                g2.setColor(new Color(rulerColor.getRed(), rulerColor.getGreen(), rulerColor.getBlue(), 120));
                g2.fillRect(0, rulerY + 10, getWidth(), 2);
            }
        };
    }

    /** Creates the line focus overlay (dims non-current lines) */
    JPanel createLineFocusOverlay() {
        return new JPanel() {
            private int focusY = 0;
            private int lineH = 24;
            {
                setOpaque(false);
            }
            public void setFocusLine(int y, int height) { this.focusY = y; this.lineH = height; repaint(); }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!enabled || !lineFocus) return;
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(0, 0, 0, 80));
                // Dim above current line
                g2.fillRect(0, 0, getWidth(), focusY);
                // Dim below current line
                g2.fillRect(0, focusY + lineH, getWidth(), getHeight() - focusY - lineH);
            }
        };
    }

    /** Opens settings dialog, returns true if settings changed */
    boolean showSettingsDialog(JFrame parent) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));

        JCheckBox enableCb = new JCheckBox("Enable Dyslexia Mode", enabled);
        JCheckBox rulerCb = new JCheckBox("Reading Ruler (highlight line)", readingRuler);
        JCheckBox focusCb = new JCheckBox("Line Focus (dim other lines)", lineFocus);

        JPanel letterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        letterPanel.add(new JLabel("Letter Spacing:"));
        JSpinner letterSp = new JSpinner(new SpinnerNumberModel((double)letterSpacing, 0.0, 0.5, 0.05));
        letterPanel.add(letterSp);

        JPanel wordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wordPanel.add(new JLabel("Word Spacing:"));
        JSpinner wordSp = new JSpinner(new SpinnerNumberModel((double)wordSpacing, 0.0, 1.0, 0.1));
        wordPanel.add(wordSp);

        JPanel linePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linePanel.add(new JLabel("Max Line Width (chars):"));
        JSpinner lineSp = new JSpinner(new SpinnerNumberModel(maxLineWidth, 40, 120, 5));
        linePanel.add(lineSp);

        JButton bgBtn = new JButton("Background Color");
        bgBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(parent, "Dyslexia Background", bgColor);
            if (c != null) bgColor = c;
        });

        JButton textBtn = new JButton("Text Color");
        textBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(parent, "Text Color", textColor);
            if (c != null) textColor = c;
        });

        panel.add(enableCb);
        panel.add(rulerCb);
        panel.add(focusCb);
        panel.add(letterPanel);
        panel.add(wordPanel);
        panel.add(linePanel);
        panel.add(bgBtn);
        panel.add(textBtn);

        int r = JOptionPane.showConfirmDialog(parent, panel, "Dyslexia Mode Settings", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return false;

        enabled = enableCb.isSelected();
        readingRuler = rulerCb.isSelected();
        lineFocus = focusCb.isSelected();
        letterSpacing = ((Number) letterSp.getValue()).floatValue();
        wordSpacing = ((Number) wordSp.getValue()).floatValue();
        maxLineWidth = (int) lineSp.getValue();
        return true;
    }
}
