/**
 * MarkdownRenderer.java — Simple Markdown to HTML renderer for notebook.me v5.0.0
 * Supports: headings, bold, italic, code, links, images, tables, lists, blockquotes, hr
 */
public class MarkdownRenderer {

    /** Convert markdown text to HTML for display in JEditorPane */
    public static String toHTML(String markdown, String bgColor, String fgColor, String accentColor) {
        if (markdown == null || markdown.isEmpty()) return "<html><body></body></html>";

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body{background:").append(bgColor).append(";color:").append(fgColor);
        html.append(";font-family:'Segoe UI',sans-serif;font-size:13px;padding:16px;line-height:1.6;}");
        html.append("h1{font-size:24px;border-bottom:2px solid ").append(accentColor).append(";padding-bottom:6px;}");
        html.append("h2{font-size:20px;border-bottom:1px solid ").append(accentColor).append(";padding-bottom:4px;}");
        html.append("h3{font-size:17px;} h4{font-size:15px;} h5{font-size:13px;} h6{font-size:12px;}");
        html.append("code{background:rgba(128,128,128,0.2);padding:2px 5px;border-radius:3px;font-family:Consolas,monospace;}");
        html.append("pre{background:rgba(0,0,0,0.3);padding:12px;border-radius:6px;overflow-x:auto;}");
        html.append("pre code{background:none;padding:0;}");
        html.append("blockquote{border-left:4px solid ").append(accentColor).append(";margin:8px 0;padding:4px 16px;opacity:0.85;}");
        html.append("table{border-collapse:collapse;width:100%;margin:8px 0;}");
        html.append("th,td{border:1px solid ").append(accentColor).append(";padding:6px 10px;text-align:left;}");
        html.append("th{background:rgba(128,128,128,0.2);font-weight:bold;}");
        html.append("a{color:").append(accentColor).append(";}");
        html.append("img{max-width:100%;border-radius:6px;margin:4px 0;}");
        html.append("hr{border:none;border-top:2px solid ").append(accentColor).append(";margin:16px 0;}");
        html.append("ul,ol{padding-left:24px;}");
        html.append("sub,sup{font-size:0.75em;}");
        html.append("</style></head><body>");

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inTable = false;
        boolean inList = false;
        String listTag = "ul";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Code blocks
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) { html.append("</code></pre>"); inCodeBlock = false; }
                else { html.append("<pre><code>"); inCodeBlock = true; }
                continue;
            }
            if (inCodeBlock) { html.append(escapeHtml(line)).append("\n"); continue; }

            // Tables
            if (line.trim().matches("\\|.*\\|")) {
                if (!inTable) { html.append("<table>"); inTable = true; }
                // Skip separator rows
                if (line.trim().matches("[\\|\\s:\\-]+")) continue;
                String[] cells = line.trim().split("\\|");
                boolean isHeader = (i + 1 < lines.length && lines[i + 1].trim().matches("[\\|\\s:\\-]+"));
                html.append("<tr>");
                for (String cell : cells) {
                    cell = cell.trim();
                    if (cell.isEmpty()) continue;
                    String tag = isHeader ? "th" : "td";
                    html.append("<").append(tag).append(">").append(processInline(cell)).append("</").append(tag).append(">");
                }
                html.append("</tr>");
                continue;
            } else if (inTable) { html.append("</table>"); inTable = false; }

            // Close list if non-list line
            if (inList && !line.trim().matches("^[-*+]\\s.*") && !line.trim().matches("^\\d+\\.\\s.*")) {
                html.append("</").append(listTag).append(">"); inList = false;
            }

            String trimmed = line.trim();

            // Horizontal rule
            if (trimmed.matches("^[-*_]{3,}$")) { html.append("<hr>"); continue; }

            // Headings
            if (trimmed.startsWith("######")) { html.append("<h6>").append(processInline(trimmed.substring(6).trim())).append("</h6>"); continue; }
            if (trimmed.startsWith("#####")) { html.append("<h5>").append(processInline(trimmed.substring(5).trim())).append("</h5>"); continue; }
            if (trimmed.startsWith("####")) { html.append("<h4>").append(processInline(trimmed.substring(4).trim())).append("</h4>"); continue; }
            if (trimmed.startsWith("###")) { html.append("<h3>").append(processInline(trimmed.substring(3).trim())).append("</h3>"); continue; }
            if (trimmed.startsWith("##")) { html.append("<h2>").append(processInline(trimmed.substring(2).trim())).append("</h2>"); continue; }
            if (trimmed.startsWith("#")) { html.append("<h1>").append(processInline(trimmed.substring(1).trim())).append("</h1>"); continue; }

            // Blockquote
            if (trimmed.startsWith(">")) { html.append("<blockquote>").append(processInline(trimmed.substring(1).trim())).append("</blockquote>"); continue; }

            // Unordered list
            if (trimmed.matches("^[-*+]\\s.*")) {
                if (!inList) { listTag = "ul"; html.append("<ul>"); inList = true; }
                html.append("<li>").append(processInline(trimmed.substring(2).trim())).append("</li>");
                continue;
            }
            // Ordered list
            if (trimmed.matches("^\\d+\\.\\s.*")) {
                if (!inList) { listTag = "ol"; html.append("<ol>"); inList = true; }
                html.append("<li>").append(processInline(trimmed.replaceFirst("^\\d+\\.\\s*", ""))).append("</li>");
                continue;
            }

            // Empty line
            if (trimmed.isEmpty()) { html.append("<br>"); continue; }

            // Paragraph
            html.append("<p>").append(processInline(line)).append("</p>");
        }

        if (inCodeBlock) html.append("</code></pre>");
        if (inTable) html.append("</table>");
        if (inList) html.append("</").append(listTag).append(">");

        html.append("</body></html>");
        return html.toString();
    }

    /** Process inline markdown: bold, italic, code, links, images, sub/sup */
    private static String processInline(String text) {
        // Images: ![alt](url)
        text = text.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\">");
        // Links: [text](url)
        text = text.replaceAll("\\[([^\\]]*)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        // Bold+italic
        text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<b><i>$1</i></b>");
        // Bold
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        text = text.replaceAll("__(.+?)__", "<b>$1</b>");
        // Italic
        text = text.replaceAll("\\*(.+?)\\*", "<i>$1</i>");
        text = text.replaceAll("_(.+?)_", "<i>$1</i>");
        // Strikethrough
        text = text.replaceAll("~~(.+?)~~", "<s>$1</s>");
        // Inline code
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");
        // Subscript <sub>
        text = text.replaceAll("<sub>(.+?)</sub>", "<sub>$1</sub>");
        // Superscript <sup>
        text = text.replaceAll("<sup>(.+?)</sup>", "<sup>$1</sup>");
        return text;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Convert hex Color to CSS string */
    public static String colorToCSS(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
