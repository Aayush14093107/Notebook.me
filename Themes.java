import java.awt.*;

// ════════════════════════════════════════════════════════
//  Abstract Base Theme + All 6 Theme Implementations
// ════════════════════════════════════════════════════════
abstract class BaseTheme implements Theme {
    protected static final Font MONO_FONT   = new Font("JetBrains Mono", Font.PLAIN, 15);
    protected static final Font LABEL_FONT  = new Font("Segoe UI", Font.PLAIN, 12);
    protected static final Font STATUS_FONT = new Font("Consolas", Font.PLAIN, 11);
    public BaseTheme() {}
    @Override public abstract String getName();
    public String describe() { return "Theme: " + getName(); }
}

// ── Ink (Dark) ───────────────────────────────────────
class InkTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(15, 17, 21); }
    @Override public Color getForeground() { return new Color(220, 210, 190); }
    @Override public Color getAccent()     { return new Color(251, 191, 36); }
    @Override public Color getSecondary()  { return new Color(45, 50, 60); }
    @Override public Color getBorder()     { return new Color(55, 60, 70); }
    @Override public Color getMenuBg()     { return new Color(20, 22, 28); }
    @Override public Color getStatusBg()   { return new Color(18, 20, 26); }
    @Override public String getName()      { return "Ink"; }
}

// ── Parchment (Light) ───────────────────────────────
class ParchmentTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(253, 249, 240); }
    @Override public Color getForeground() { return new Color(40, 35, 28); }
    @Override public Color getAccent()     { return new Color(180, 80, 20); }
    @Override public Color getSecondary()  { return new Color(240, 232, 216); }
    @Override public Color getBorder()     { return new Color(200, 188, 168); }
    @Override public Color getMenuBg()     { return new Color(245, 238, 225); }
    @Override public Color getStatusBg()   { return new Color(238, 229, 212); }
    @Override public String getName()      { return "Parchment"; }
}

// ── Mocha (Warm Dark Brown) ─────────────────────────
class MochaTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(30, 25, 22); }
    @Override public Color getForeground() { return new Color(210, 195, 170); }
    @Override public Color getAccent()     { return new Color(205, 133, 63); }
    @Override public Color getSecondary()  { return new Color(50, 40, 35); }
    @Override public Color getBorder()     { return new Color(70, 55, 45); }
    @Override public Color getMenuBg()     { return new Color(35, 28, 24); }
    @Override public Color getStatusBg()   { return new Color(28, 22, 18); }
    @Override public String getName()      { return "Mocha"; }
}

// ── Ocean (Deep Blue-Green) ─────────────────────────
class OceanTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(12, 20, 30); }
    @Override public Color getForeground() { return new Color(180, 210, 230); }
    @Override public Color getAccent()     { return new Color(0, 180, 216); }
    @Override public Color getSecondary()  { return new Color(25, 40, 55); }
    @Override public Color getBorder()     { return new Color(40, 60, 80); }
    @Override public Color getMenuBg()     { return new Color(15, 25, 35); }
    @Override public Color getStatusBg()   { return new Color(10, 18, 28); }
    @Override public String getName()      { return "Ocean"; }
}

// ── Sunset (Warm Orange/Red) ────────────────────────
class SunsetTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(25, 15, 18); }
    @Override public Color getForeground() { return new Color(230, 200, 185); }
    @Override public Color getAccent()     { return new Color(255, 107, 53); }
    @Override public Color getSecondary()  { return new Color(50, 30, 35); }
    @Override public Color getBorder()     { return new Color(75, 45, 50); }
    @Override public Color getMenuBg()     { return new Color(30, 18, 22); }
    @Override public Color getStatusBg()   { return new Color(22, 13, 16); }
    @Override public String getName()      { return "Sunset"; }
}

// ── Forest (Deep Green) ─────────────────────────────
class ForestTheme extends BaseTheme {
    @Override public Color getBackground() { return new Color(15, 25, 18); }
    @Override public Color getForeground() { return new Color(195, 220, 200); }
    @Override public Color getAccent()     { return new Color(80, 200, 120); }
    @Override public Color getSecondary()  { return new Color(30, 45, 35); }
    @Override public Color getBorder()     { return new Color(45, 65, 50); }
    @Override public Color getMenuBg()     { return new Color(18, 28, 20); }
    @Override public Color getStatusBg()   { return new Color(12, 22, 15); }
    @Override public String getName()      { return "Forest"; }
}
