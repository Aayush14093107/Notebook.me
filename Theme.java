import java.awt.*;

// ════════════════════════════════════════════════════════
//  Theme Contract Interface
// ════════════════════════════════════════════════════════
interface Theme {
    Color getBackground();
    Color getForeground();
    Color getAccent();
    Color getSecondary();
    Color getBorder();
    Color getMenuBg();
    Color getStatusBg();
    String getName();
}
