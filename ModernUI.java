import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;

public final class ModernUI {
    private static final String BUTTON_HOVER_KEY = "modern.button.hover";
    private static final String FIELD_FOCUS_KEY = "modern.field.focus";
    public static final int RADIUS = 8;

    private ModernUI() {}

    public static void apply(Theme theme) {
        Color bg = theme.getBackground();
        Color fg = theme.getForeground();
        Color menu = theme.getMenuBg();
        Color accent = theme.getAccent();
        Color border = hairline(theme);
        Color panel = panelColor(theme);
        Color input = inputColor(theme);

        UIManager.put("control", panel);
        UIManager.put("info", panel);
        UIManager.put("nimbusBase", accent);
        UIManager.put("nimbusBlueGrey", panel);
        UIManager.put("nimbusLightBackground", bg);
        UIManager.put("text", fg);

        UIManager.put("Panel.background", bg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("Label.font", uiFont(Font.PLAIN, 13f));

        UIManager.put("MenuBar.background", menu);
        UIManager.put("MenuBar.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));
        UIManager.put("Menu.font", uiFont(Font.PLAIN, 12f));
        UIManager.put("Menu.foreground", mix(fg, accent, 0.12f));
        UIManager.put("Menu.selectionBackground", accentSoft(theme));
        UIManager.put("Menu.selectionForeground", fg);
        UIManager.put("MenuItem.background", cardColor(theme));
        UIManager.put("MenuItem.foreground", fg);
        UIManager.put("MenuItem.font", uiFont(Font.PLAIN, 12f));
        UIManager.put("MenuItem.selectionBackground", accentSoft(theme));
        UIManager.put("MenuItem.selectionForeground", fg);
        UIManager.put("MenuItem.acceleratorForeground", withAlpha(fg, 150));
        UIManager.put("MenuItem.border", BorderFactory.createEmptyBorder(8, 12, 8, 12));
        UIManager.put("CheckBoxMenuItem.background", cardColor(theme));
        UIManager.put("CheckBoxMenuItem.foreground", fg);
        UIManager.put("CheckBoxMenuItem.selectionBackground", accentSoft(theme));
        UIManager.put("CheckBoxMenuItem.selectionForeground", fg);
        UIManager.put("CheckBoxMenuItem.border", BorderFactory.createEmptyBorder(8, 12, 8, 12));
        UIManager.put("CheckBoxMenuItem.checkIcon", createCheckIcon(theme, false));
        UIManager.put("RadioButtonMenuItem.background", cardColor(theme));
        UIManager.put("RadioButtonMenuItem.foreground", fg);
        UIManager.put("RadioButtonMenuItem.selectionBackground", accentSoft(theme));
        UIManager.put("RadioButtonMenuItem.selectionForeground", fg);
        UIManager.put("RadioButtonMenuItem.border", BorderFactory.createEmptyBorder(8, 12, 8, 12));
        UIManager.put("RadioButtonMenuItem.checkIcon", createCheckIcon(theme, true));
        UIManager.put("PopupMenu.background", cardColor(theme));
        UIManager.put("PopupMenu.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        UIManager.put("Separator.foreground", border);

        UIManager.put("CheckBox.icon", createCheckIcon(theme, false));
        UIManager.put("RadioButton.icon", createCheckIcon(theme, true));

        UIManager.put("Button.background", cardColor(theme));
        UIManager.put("Button.foreground", fg);
        UIManager.put("Button.font", uiFont(Font.BOLD, 12f));
        UIManager.put("Button.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("Button.select", accentSoft(theme));

        UIManager.put("TextField.background", input);
        UIManager.put("TextField.foreground", fg);
        UIManager.put("TextField.caretForeground", accent);
        UIManager.put("TextField.font", uiFont(Font.PLAIN, 13f));
        UIManager.put("TextField.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        UIManager.put("FormattedTextField.background", input);
        UIManager.put("FormattedTextField.foreground", fg);
        UIManager.put("FormattedTextField.caretForeground", accent);
        UIManager.put("FormattedTextField.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        UIManager.put("TextArea.background", bg);
        UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextArea.caretForeground", accent);
        UIManager.put("TextArea.font", monoFont(Font.PLAIN, 14f));
        UIManager.put("TextArea.selectionBackground", withAlpha(accent, 120));
        UIManager.put("TextArea.selectionForeground", fg);
        UIManager.put("PasswordField.background", input);
        UIManager.put("PasswordField.foreground", fg);
        UIManager.put("PasswordField.caretForeground", accent);
        UIManager.put("PasswordField.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));

        UIManager.put("ComboBox.background", input);
        UIManager.put("ComboBox.foreground", fg);
        UIManager.put("ComboBox.selectionBackground", accentSoft(theme));
        UIManager.put("ComboBox.selectionForeground", fg);
        UIManager.put("ComboBox.font", uiFont(Font.PLAIN, 12f));
        UIManager.put("ComboBox.border", new RoundedBorder(border, RADIUS, 1));

        UIManager.put("ScrollPane.background", panel);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", bg);

        UIManager.put("TabbedPane.background", menu);
        UIManager.put("TabbedPane.foreground", fg);
        UIManager.put("TabbedPane.tabInsets", new Insets(7, 12, 7, 12));
        UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(4, 4, 0, 4));
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.shadow", menu);
        UIManager.put("TabbedPane.darkShadow", menu);
        UIManager.put("TabbedPane.light", menu);
        UIManager.put("TabbedPane.highlight", menu);

        UIManager.put("Tree.background", panelColor(theme));
        UIManager.put("Tree.foreground", fg);
        UIManager.put("Tree.selectionBackground", accentSoft(theme));
        UIManager.put("Tree.selectionForeground", fg);
        UIManager.put("Tree.rowHeight", 30);
        UIManager.put("Tree.font", uiFont(Font.PLAIN, 13f));
        UIManager.put("Tree.hash", border);
        UIManager.put("Tree.line", border);

        UIManager.put("List.background", panel);
        UIManager.put("List.foreground", fg);
        UIManager.put("List.selectionBackground", accentSoft(theme));
        UIManager.put("List.selectionForeground", fg);
        UIManager.put("List.font", uiFont(Font.PLAIN, 12f));

        UIManager.put("OptionPane.background", menu);
        UIManager.put("OptionPane.foreground", fg);
        UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("OptionPane.buttonFont", uiFont(Font.BOLD, 12f));

        UIManager.put("ToolTip.background", cardColor(theme));
        UIManager.put("ToolTip.foreground", fg);
        UIManager.put("ToolTip.border", new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    public static Font uiFont(int style, float size) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    public static Font monoFont(int style, float size) {
        return new Font("JetBrains Mono", style, Math.round(size));
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
    }

    public static Color mix(Color a, Color b, float ratio) {
        float safe = Math.max(0f, Math.min(1f, ratio));
        int r = Math.round(a.getRed() * (1f - safe) + b.getRed() * safe);
        int g = Math.round(a.getGreen() * (1f - safe) + b.getGreen() * safe);
        int bl = Math.round(a.getBlue() * (1f - safe) + b.getBlue() * safe);
        int al = Math.round(a.getAlpha() * (1f - safe) + b.getAlpha() * safe);
        return new Color(clamp(r), clamp(g), clamp(bl), clamp(al));
    }

    public static Color shift(Color color, int amount) {
        return new Color(
            clamp(color.getRed() + amount),
            clamp(color.getGreen() + amount),
            clamp(color.getBlue() + amount),
            color.getAlpha());
    }

    public static Color hairline(Theme theme) {
        return mix(theme.getBorder(), theme.getForeground(), 0.08f);
    }

    public static Color cardColor(Theme theme) {
        return mix(theme.getMenuBg(), theme.getSecondary(), 0.45f);
    }

    public static Color panelColor(Theme theme) {
        return mix(theme.getBackground(), theme.getMenuBg(), 0.34f);
    }

    public static Color editorColor(Theme theme) {
        return mix(theme.getBackground(), theme.getMenuBg(), 0.14f);
    }

    public static Color inputColor(Theme theme) {
        return mix(theme.getSecondary(), theme.getBackground(), 0.22f);
    }

    public static Color accentSoft(Theme theme) {
        return mix(theme.getAccent(), theme.getMenuBg(), 0.72f);
    }

    public static Color contrastText(Color background) {
        double luminance = (0.299 * background.getRed()) + (0.587 * background.getGreen()) + (0.114 * background.getBlue());
        return luminance >= 150 ? new Color(20, 24, 28) : Color.WHITE;
    }

    public static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static void styleButton(AbstractButton button, Theme theme, String variant) {
        Color background;
        Color foreground;
        Color hover;
        Color border = hairline(theme);

        switch (variant) {
            case "primary":
                background = theme.getAccent();
                foreground = contrastText(background);
                hover = shift(background, foreground == Color.WHITE ? 16 : -10);
                border = mix(theme.getAccent(), theme.getForeground(), 0.18f);
                break;
            case "ghost":
                background = withAlpha(cardColor(theme), 180);
                foreground = mix(theme.getForeground(), theme.getAccent(), 0.18f);
                hover = accentSoft(theme);
                break;
            case "danger":
                background = new Color(176, 64, 64);
                foreground = Color.WHITE;
                hover = new Color(196, 82, 82);
                border = new Color(140, 50, 50);
                break;
            default:
                background = cardColor(theme);
                foreground = theme.getForeground();
                hover = shift(background, 10);
                break;
        }

        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(uiFont(Font.BOLD, 12f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setBorder(new CompoundBorder(
            new RoundedBorder(border, RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        MouseListener previous = (MouseListener) ((JComponent) button).getClientProperty(BUTTON_HOVER_KEY);
        if (previous != null) {
            button.removeMouseListener(previous);
        }
        MouseAdapter hoverListener = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(hover); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(background); }
        };
        button.addMouseListener(hoverListener);
        ((JComponent) button).putClientProperty(BUTTON_HOVER_KEY, hoverListener);
    }

    public static void styleTextField(JTextComponent field, Theme theme, boolean monospaced) {
        Color normalBorder = hairline(theme);
        Color focusBorder = mix(theme.getAccent(), theme.getForeground(), 0.16f);
        Border normal = new CompoundBorder(
            new RoundedBorder(normalBorder, RADIUS, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10));
        Border focused = new CompoundBorder(
            new RoundedBorder(focusBorder, RADIUS, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10));

        field.setBackground(inputColor(theme));
        field.setForeground(theme.getForeground());
        field.setCaretColor(theme.getAccent());
        field.setFont(monospaced ? monoFont(Font.PLAIN, 13f) : uiFont(Font.PLAIN, 13f));
        field.setBorder(normal);
        field.setSelectionColor(withAlpha(theme.getAccent(), 125));
        field.setSelectedTextColor(theme.getForeground());

        FocusListener previous = (FocusListener) ((JComponent) field).getClientProperty(FIELD_FOCUS_KEY);
        if (previous != null) {
            field.removeFocusListener(previous);
        }
        FocusAdapter focusListener = new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.setBorder(focused); }
            @Override public void focusLost(FocusEvent e) { field.setBorder(normal); }
        };
        field.addFocusListener(focusListener);
        ((JComponent) field).putClientProperty(FIELD_FOCUS_KEY, focusListener);
    }

    public static void styleComboBox(JComboBox<?> comboBox, Theme theme) {
        comboBox.setBackground(inputColor(theme));
        comboBox.setForeground(theme.getForeground());
        comboBox.setFont(uiFont(Font.PLAIN, 12f));
        comboBox.setBorder(new RoundedBorder(hairline(theme), RADIUS, 1));
        comboBox.setFocusable(false);
    }

    public static void styleSpinner(JSpinner spinner, Theme theme) {
        spinner.setBorder(new RoundedBorder(hairline(theme), RADIUS, 1));
        spinner.setBackground(inputColor(theme));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
            styleTextField(field, theme, false);
            field.setHorizontalAlignment(JTextField.CENTER);
        }
    }

    public static void styleCheckBox(JCheckBox box, Theme theme) {
        box.setOpaque(false);
        box.setForeground(theme.getForeground());
        box.setFont(uiFont(Font.PLAIN, 12f));
        box.setFocusPainted(false);
    }

    public static void styleList(JList<?> list, Theme theme) {
        list.setBackground(panelColor(theme));
        list.setForeground(theme.getForeground());
        list.setSelectionBackground(accentSoft(theme));
        list.setSelectionForeground(theme.getForeground());
        list.setFont(uiFont(Font.PLAIN, 12f));
        list.setFixedCellHeight(34);
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    public static void styleScrollPane(JScrollPane scrollPane, Theme theme, Color viewportBackground) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(viewportBackground);
        scrollPane.setOpaque(false);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI(theme));
        scrollPane.getHorizontalScrollBar().setUI(new ModernScrollBarUI(theme));
    }

    public static void styleSplitPane(JSplitPane splitPane, Theme theme) {
        splitPane.setUI(new ModernSplitPaneUI(theme));
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setContinuousLayout(true);
    }

    private static Icon createCheckIcon(Theme theme, boolean isRadio) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                AbstractButton b = (AbstractButton) c;
                ButtonModel model = b.getModel();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = getIconWidth();
                boolean selected = model.isSelected();
                
                Color bg = selected ? theme.getAccent() : inputColor(theme);
                Color border = selected ? mix(theme.getAccent(), theme.getForeground(), 0.1f) : hairline(theme);
                
                g2.setColor(bg);
                if (isRadio) {
                    g2.fillOval(x, y, size, size);
                    g2.setColor(border);
                    g2.drawOval(x, y, size - 1, size - 1);
                    if (selected) {
                        g2.setColor(contrastText(bg));
                        g2.fillOval(x + 4, y + 4, size - 8, size - 8);
                    }
                } else {
                    g2.fillRoundRect(x, y, size, size, 4, 4);
                    g2.setColor(border);
                    g2.drawRoundRect(x, y, size - 1, size - 1, 4, 4);
                    if (selected) {
                        g2.setColor(contrastText(bg));
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(x + 3, y + size / 2, x + size / 2 - 1, y + size - 3);
                        g2.drawLine(x + size / 2 - 1, y + size - 3, x + size - 3, y + 3);
                    }
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return 14; }

            @Override
            public int getIconHeight() { return 14; }
        };
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}

class RoundedBorder extends AbstractBorder {
    private final Color color;
    private final int arc;
    private final int thickness;

    RoundedBorder(Color color, int arc) {
        this(color, arc, 1);
    }

    RoundedBorder(Color color, int arc, int thickness) {
        this.color = color;
        this.arc = arc;
        this.thickness = thickness;
    }

    @Override
    public Insets getBorderInsets(Component component, Insets insets) {
        int inset = thickness + 1;
        insets.set(inset, inset, inset, inset);
        return insets;
    }

    @Override
    public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        g2.draw(new RoundRectangle2D.Float(
            x + (thickness / 2f),
            y + (thickness / 2f),
            width - thickness,
            height - thickness,
            arc,
            arc));
        g2.dispose();
    }
}

class SurfacePanel extends JPanel {
    private Color fillColor;
    private Color borderColor;
    private int arc;

    SurfacePanel(LayoutManager layout, Color fillColor, Color borderColor, int arc) {
        super(layout);
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.arc = arc;
        setOpaque(false);
    }

    void setColors(Color fillColor, Color borderColor) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fillColor);
        if (arc > 0) {
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } else {
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        if (borderColor != null) {
            g2.setColor(borderColor);
            if (arc > 0) {
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } else {
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}

class GradientPanel extends JPanel {
    private Color startColor;
    private Color endColor;
    private Color borderColor;
    private int arc;

    GradientPanel(LayoutManager layout, Color startColor, Color endColor, Color borderColor, int arc) {
        super(layout);
        this.startColor = startColor;
        this.endColor = endColor;
        this.borderColor = borderColor;
        this.arc = arc;
        setOpaque(false);
    }

    void setColors(Color startColor, Color endColor, Color borderColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        this.borderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, startColor, getWidth(), getHeight(), endColor));
        if (arc > 0) {
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } else {
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        if (borderColor != null) {
            g2.setColor(borderColor);
            if (arc > 0) {
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } else {
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}

class ModernScrollBarUI extends BasicScrollBarUI {
    private final Theme theme;

    ModernScrollBarUI(Theme theme) {
        this.theme = theme;
    }

    @Override
    protected void configureScrollBarColors() {
        thumbColor = ModernUI.mix(theme.getAccent(), theme.getMenuBg(), 0.45f);
        trackColor = ModernUI.mix(theme.getBackground(), theme.getMenuBg(), 0.2f);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    private JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setColor(trackColor);
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(thumbColor);
        g2.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 8, 8);
        g2.dispose();
    }
}

class ModernSplitPaneUI extends BasicSplitPaneUI {
    private final Theme theme;

    ModernSplitPaneUI(Theme theme) {
        this.theme = theme;
    }

    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernUI.hairline(theme));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        divider.setBorder(BorderFactory.createEmptyBorder());
        return divider;
    }
}

class ModernTabbedPaneUI extends BasicTabbedPaneUI {
    private final Theme theme;

    ModernTabbedPaneUI(Theme theme) {
        this.theme = theme;
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(7, 12, 7, 12);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(4, 4, 0, 4);
        contentBorderInsets = new Insets(0, 0, 0, 0);
    }

    @Override
    protected Insets getTabInsets(int tabPlacement, int tabIndex) {
        return new Insets(7, 12, 7, 12);
    }

    @Override
    protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = isSelected
            ? ModernUI.mix(theme.getAccent(), theme.getMenuBg(), 0.72f)
            : ModernUI.mix(theme.getMenuBg(), theme.getBackground(), 0.24f);
        g2.setColor(fill);
        g2.fillRoundRect(x + 2, y + 3, width - 4, height - 6, ModernUI.RADIUS, ModernUI.RADIUS);
        if (isSelected) {
            g2.setColor(ModernUI.withAlpha(theme.getAccent(), 185));
            g2.fillRoundRect(x + 8, y + height - 7, Math.max(18, width - 16), 3, 3, 3);
        }
        g2.dispose();
    }

    @Override
    protected void paintTabBorder(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ModernUI.hairline(theme));
        g2.drawRoundRect(x + 2, y + 3, width - 5, height - 7, ModernUI.RADIUS, ModernUI.RADIUS);
        g2.dispose();
    }

    @Override
    protected void paintFocusIndicator(Graphics graphics, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
    }

    @Override
    protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex) {
    }
}
