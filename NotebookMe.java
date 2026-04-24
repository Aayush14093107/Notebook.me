/**
 * notebook.me v5.0.0 — Feature-rich Java Notepad
 */
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import javax.swing.tree.*;
import javax.swing.undo.*;

public class NotebookMe extends JFrame {
    private static final String APP_NAME = "notebook.me";
    private static final String VERSION  = "5.0.0";
    private static int instanceCount = 0;
    private Theme currentTheme;
    private boolean wordWrap = true;
    private int fontSize = 15;
    private String fontFamily = "JetBrains Mono";
    private Color fontColor = null;
    private boolean showLineNumbers = true;
    private boolean autoSaveEnabled = true;
    private boolean use24HourClock = true;
    private DyslexiaMode dyslexiaMode = new DyslexiaMode();
    private JTabbedPane tabbedPane;
    private List<TabData> tabs = new ArrayList<>();
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private File notebookDir;
    private JSplitPane splitPane;
    private boolean sidebarVisible = true;
    private JLabel statusLeft, statusMid, statusRight, lastEditedLabel;
    private JPanel statusBar;
    private JMenuBar menuBar;
    private JTextField searchField;
    private Thread autoSaveThread;
    private volatile boolean running = true;
    private JPanel scanlineOverlay;
    private JButton sideToggleBtn;
    private boolean isFullScreen = false;
    private boolean readOnlyMode = false;
    private JSplitPane mdSplitPane;
    private JEditorPane mdPreviewPane;
    private boolean mdPreviewVisible = false;

    static class TabData {
        JTextArea textArea;
        JPanel editorPanel;
        LineNumberPanel linePanel;
        UndoManager undoManager;
        File file;
        boolean modified;
        String lastSaved;
        boolean pinned;
        List<String> versionHistory;
        javax.swing.Timer selfDestructTimer;
        long selfDestructTime;
        TabData() {
            undoManager = new UndoManager();
            undoManager.setLimit(500);
            modified = false; lastSaved = ""; pinned = false;
            versionHistory = new ArrayList<>(); selfDestructTime = 0;
        }
    }

    static class LineNumberPanel extends JPanel {
        private JTextArea textArea;
        private Theme theme;
        LineNumberPanel(JTextArea ta, Theme t) { this.textArea = ta; this.theme = t; setPreferredSize(new Dimension(48, 0)); }
        void setTheme(Theme t) { this.theme = t; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            setBackground(theme.getMenuBg());
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font("Consolas", Font.PLAIN, textArea.getFont().getSize() - 1));
            g2.setColor(new Color(theme.getForeground().getRed(), theme.getForeground().getGreen(), theme.getForeground().getBlue(), 100));
            int lineHeight = textArea.getFontMetrics(textArea.getFont()).getHeight();
            Rectangle clip = g2.getClipBounds();
            int startLine = clip.y / lineHeight;
            int endLine = (clip.y + clip.height) / lineHeight + 1;
            int totalLines = textArea.getLineCount();
            FontMetrics fm = g2.getFontMetrics();
            for (int i = startLine; i <= Math.min(endLine, totalLines - 1); i++) {
                String num = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(num) - 8;
                int y = (i + 1) * lineHeight - 4;
                g2.drawString(num, x, y);
            }
        }
    }

    public NotebookMe() {
        instanceCount++;
        currentTheme = new InkTheme();
        notebookDir = new File(System.getProperty("user.home"), ".notebookme");
        if (!notebookDir.exists()) notebookDir.mkdirs();
        initLookAndFeel(); initComponents(); initMenuBar(); initStatusBar(); initAutoSave(); initWindowEvents();
        setTitle(APP_NAME + "  ·  untitled");
        setSize(1100, 750); setMinimumSize(new Dimension(700, 500));
        setIconImage(createAppIcon());
        setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setVisible(true);
        getCurrentTextArea().requestFocusInWindow(); showWelcome();
    }

    private void initLookAndFeel() {
        try {
            // Use system (Windows) look and feel for modern file dialogs
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    /** Programmatically draw the Notebook.Me logo icon — 128×128 */
    private java.awt.Image createAppIcon() {
        int s = 128;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Notebook body — dark charcoal
        g.setColor(new Color(50, 50, 50));
        g.fillRoundRect(12, 8, 104, 112, 18, 18);
        // Lighter inner area
        g.setColor(new Color(62, 62, 62));
        g.fillRoundRect(20, 16, 88, 96, 12, 12);
        // Two horizontal white lines
        g.setColor(new Color(200, 200, 200));
        g.fillRoundRect(40, 66, 48, 3, 3, 3);
        g.fillRoundRect(40, 78, 48, 3, 3, 3);
        // Gold text "N.Me"
        g.setColor(new Color(195, 170, 120));
        g.setFont(new Font("Georgia", Font.BOLD, 22));
        g.drawString("N.Me", 32, 54);
        // Bookmark ribbon
        g.setColor(new Color(80, 65, 50));
        int[] bx = {88, 102, 102, 95, 88};
        int[] by = {100, 100, 128, 115, 128};
        g.fillPolygon(bx, by, 5);
        g.dispose();
        return img;
    }

    private void initComponents() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(currentTheme.getBackground());
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(currentTheme.getMenuBg());
        tabbedPane.setForeground(currentTheme.getForeground());
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabbedPane.addChangeListener(e -> onTabChanged());
        addNewTab("untitled", null);
        rootNode = new DefaultMutableTreeNode("📓 Notebooks");
        treeModel = new DefaultTreeModel(rootNode);
        folderTree = new JTree(treeModel);
        folderTree.setBackground(currentTheme.getMenuBg());
        folderTree.setForeground(currentTheme.getForeground());
        folderTree.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
        folderTree.setRowHeight(28);
        folderTree.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        folderTree.setCellRenderer(new ModernTreeRenderer());
        folderTree.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount()==2) openFromTree(); } });
        loadFolderTree();
        JScrollPane treeScroll = new JScrollPane(folderTree);
        treeScroll.setPreferredSize(new Dimension(210, 0));
        treeScroll.setBorder(BorderFactory.createMatteBorder(0,0,0,1,currentTheme.getBorder()));
        treeScroll.getViewport().setBackground(currentTheme.getMenuBg());
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(currentTheme.getMenuBg());
        // Sidebar header label
        JLabel sideHeader = new JLabel("  📚  My Notebooks"); sideHeader.setFont(new Font("Segoe UI Emoji", Font.BOLD, 12));
        sideHeader.setForeground(currentTheme.getAccent()); sideHeader.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
        JPanel sideButtons = new JPanel(new GridLayout(1, 3, 4, 0));
        sideButtons.setBackground(currentTheme.getMenuBg());
        sideButtons.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JButton af = smallBtn("📁 Folder"); JButton an = smallBtn("📝 Note"); JButton dl = smallBtn("🗑️ Delete");
        af.addActionListener(e -> createFolder()); an.addActionListener(e -> createNoteInFolder()); dl.addActionListener(e -> deleteFromTree());
        sideButtons.add(af); sideButtons.add(an); sideButtons.add(dl);
        JPanel sideTopPanel = new JPanel(new BorderLayout());
        sideTopPanel.setBackground(currentTheme.getMenuBg());
        sideTopPanel.add(sideHeader, BorderLayout.NORTH);
        sideTopPanel.add(sideButtons, BorderLayout.SOUTH);
        sidePanel.add(sideTopPanel, BorderLayout.NORTH);
        sidePanel.add(treeScroll, BorderLayout.CENTER);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(currentTheme.getBackground());
        centerPanel.add(buildTopStrip(), BorderLayout.NORTH);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, centerPanel);
        splitPane.setDividerLocation(210); splitPane.setDividerSize(3); splitPane.setBorder(null);
        getContentPane().add(splitPane, BorderLayout.CENTER);
    }

    private JButton smallBtn(String text) {
        JButton b = new JButton(text); b.setFont(new Font("Segoe UI Emoji",Font.PLAIN,11));
        b.setBackground(currentTheme.getSecondary()); b.setForeground(currentTheme.getForeground());
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }

    private JPanel buildTopStrip() {
        JPanel strip = new JPanel(new BorderLayout());
        strip.setBackground(currentTheme.getMenuBg());
        strip.setBorder(BorderFactory.createMatteBorder(0,0,1,0,currentTheme.getBorder()));
        strip.setPreferredSize(new Dimension(0, 36));
        // Sidebar toggle button — painted triangle arrows
        sideToggleBtn = new JButton("\u25C0") {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getForeground());
                int w=getWidth(), h=getHeight(), sz=8;
                if (sidebarVisible) { // left arrow ◀
                    int[] xs={w/2+sz/2, w/2-sz/2, w/2+sz/2};
                    int[] ys={h/2-sz, h/2, h/2+sz};
                    g2.fillPolygon(xs, ys, 3);
                } else { // right arrow ▶
                    int[] xs={w/2-sz/2, w/2+sz/2, w/2-sz/2};
                    int[] ys={h/2-sz, h/2, h/2+sz};
                    g2.fillPolygon(xs, ys, 3);
                }
            }
        };
        sideToggleBtn.setText(""); // painted, no text
        sideToggleBtn.setPreferredSize(new Dimension(28, 28));
        sideToggleBtn.setForeground(currentTheme.getAccent()); sideToggleBtn.setBackground(currentTheme.getMenuBg());
        sideToggleBtn.setFocusPainted(false); sideToggleBtn.setBorderPainted(false); sideToggleBtn.setContentAreaFilled(false);
        sideToggleBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        sideToggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sideToggleBtn.setToolTipText("Toggle sidebar");
        sideToggleBtn.addActionListener(e -> animateSidebar());
        JPanel leftStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); leftStrip.setOpaque(false);
        leftStrip.add(sideToggleBtn);
        JLabel logo = new JLabel("  notebook.me"); logo.setFont(new Font("Georgia",Font.ITALIC,13)); logo.setForeground(currentTheme.getAccent());
        leftStrip.add(logo);
        strip.add(leftStrip, BorderLayout.WEST);
        JPanel sp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4)); sp.setBackground(currentTheme.getMenuBg());
        searchField = new JTextField(18); searchField.setFont(new Font("Consolas",Font.PLAIN,12));
        searchField.setBackground(currentTheme.getSecondary()); searchField.setForeground(currentTheme.getForeground());
        searchField.setCaretColor(currentTheme.getAccent());
        searchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(currentTheme.getBorder()), BorderFactory.createEmptyBorder(4,6,4,6)));
        // Placeholder simulation
        searchField.setText("Search..."); searchField.setForeground(new Color(currentTheme.getForeground().getRed(), currentTheme.getForeground().getGreen(), currentTheme.getForeground().getBlue(), 120));
        searchField.addFocusListener(new FocusAdapter(){
            public void focusGained(FocusEvent e){ if(searchField.getText().equals("Search...")){ searchField.setText(""); searchField.setForeground(currentTheme.getForeground()); }}
            public void focusLost(FocusEvent e){ if(searchField.getText().isEmpty()){ searchField.setText("Search..."); searchField.setForeground(new Color(currentTheme.getForeground().getRed(),currentTheme.getForeground().getGreen(),currentTheme.getForeground().getBlue(),120)); }}
        });
        searchField.setToolTipText("Quick Search (Enter)"); searchField.addActionListener(e -> quickSearch());
        JButton sb = new JButton("Search");
        sb.setFont(new Font("Segoe UI",Font.BOLD,11));
        sb.setBackground(currentTheme.getAccent()); sb.setForeground(currentTheme.getBackground());
        sb.setFocusPainted(false); sb.setBorderPainted(false);
        sb.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        sb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sb.addActionListener(e -> quickSearch());
        sp.add(searchField); sp.add(sb); strip.add(sp, BorderLayout.EAST);
        return strip;
    }

    private TabData addNewTab(String title, File file) {
        TabData td = new TabData(); td.file = file;
        td.textArea = new JTextArea(); td.textArea.setLineWrap(wordWrap); td.textArea.setWrapStyleWord(true); td.textArea.setTabSize(4);
        td.textArea.setBackground(currentTheme.getBackground());
        td.textArea.setForeground(fontColor != null ? fontColor : currentTheme.getForeground());
        td.textArea.setCaretColor(currentTheme.getAccent()); td.textArea.setSelectionColor(currentTheme.getAccent().darker());
        td.textArea.setSelectedTextColor(currentTheme.getBackground());
        td.textArea.setFont(new Font(fontFamily, Font.PLAIN, fontSize));
        td.textArea.setBorder(BorderFactory.createEmptyBorder(12,12,12,24));
        td.textArea.getDocument().addUndoableEditListener(e -> td.undoManager.addEdit(e.getEdit()));
        td.textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onTextChange(td); }
            public void removeUpdate(DocumentEvent e) { onTextChange(td); }
            public void changedUpdate(DocumentEvent e) { onTextChange(td); }
        });
        td.textArea.addCaretListener(e -> updateCaretStatus());
        td.textArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown()) { if(e.getKeyCode()==KeyEvent.VK_EQUALS) changeFontSize(1); else if(e.getKeyCode()==KeyEvent.VK_MINUS) changeFontSize(-1); }
            }
        });
        td.linePanel = new LineNumberPanel(td.textArea, currentTheme);
        td.editorPanel = new JPanel(new BorderLayout()); td.editorPanel.add(td.linePanel, BorderLayout.WEST);
        JScrollPane scroll = new JScrollPane(td.textArea); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setBackground(currentTheme.getBackground());
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI(currentTheme));
        scroll.getViewport().addChangeListener(e -> td.linePanel.repaint());
        td.editorPanel.add(scroll, BorderLayout.CENTER); td.linePanel.setVisible(showLineNumbers);
        tabs.add(td); tabbedPane.addTab(title, td.editorPanel);
        int idx = tabbedPane.getTabCount() - 1;
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT,4,0)); tabHeader.setOpaque(false);
        JLabel tabLabel = new JLabel(title); tabLabel.setForeground(Color.BLACK); tabLabel.setFont(new Font("Segoe UI",Font.PLAIN,11));
        JButton closeBtn = new JButton("x"); closeBtn.setFont(new Font("Segoe UI",Font.BOLD,11));
        closeBtn.setForeground(Color.BLACK); closeBtn.setBorder(BorderFactory.createEmptyBorder(0,4,0,0));
        closeBtn.setContentAreaFilled(false); closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closeTab(tabs.indexOf(td)));
        tabHeader.add(tabLabel); tabHeader.add(closeBtn);
        tabbedPane.setTabComponentAt(idx, tabHeader); tabbedPane.setSelectedIndex(idx);
        return td;
    }

    private void closeTab(int idx) {
        if (idx<0||idx>=tabs.size()) return;
        TabData td = tabs.get(idx);
        if (td.modified) { int r=JOptionPane.showConfirmDialog(this,"Save changes?","Unsaved",JOptionPane.YES_NO_CANCEL_OPTION); if(r==JOptionPane.CANCEL_OPTION)return; if(r==JOptionPane.YES_OPTION) saveTabFile(td); }
        if (td.selfDestructTimer!=null) td.selfDestructTimer.stop();
        tabs.remove(idx); tabbedPane.removeTabAt(idx);
        if (tabs.isEmpty()) addNewTab("untitled", null);
    }

    private TabData currentTab() { int i=tabbedPane.getSelectedIndex(); return(i>=0&&i<tabs.size())?tabs.get(i):null; }
    private JTextArea getCurrentTextArea() { TabData td=currentTab(); return td!=null?td.textArea:tabs.get(0).textArea; }
    private void onTabChanged() { updateStatus(); updateCaretStatus(); }

    private void initMenuBar() {
        menuBar = new JMenuBar(); menuBar.setBackground(currentTheme.getMenuBg());
        menuBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,currentTheme.getBorder()));
        menuBar.add(buildFileMenu()); menuBar.add(buildEditMenu()); menuBar.add(buildViewMenu());
        menuBar.add(buildFormatMenu()); menuBar.add(buildInsertMenu()); menuBar.add(buildToolsMenu());
        menuBar.add(buildSettingsMenu()); menuBar.add(buildThemeMenu()); menuBar.add(buildHelpMenu()); setJMenuBar(menuBar);
    }

    private JMenu buildFileMenu() {
        JMenu m=styledMenu("File");
        JMenuItem n=si("New Tab"); n.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,InputEvent.CTRL_DOWN_MASK));
        JMenuItem o=si("Open..."); o.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_DOWN_MASK));
        JMenuItem s=si("Save"); s.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK));
        JMenuItem sa=si("Save As..."); sa.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
        JMenuItem dup=si("Duplicate Tab");
        JMenuItem exp=si("Export As...");
        JMenuItem ct=si("Close Tab"); ct.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,InputEvent.CTRL_DOWN_MASK));
        JMenuItem ex=si("Exit");
        n.addActionListener(e->addNewTab("untitled",null)); o.addActionListener(e->openFile());
        s.addActionListener(e->{TabData td=currentTab();if(td!=null)saveTabFile(td);}); sa.addActionListener(e->saveFileAs());
        dup.addActionListener(e->duplicateTab());
        exp.addActionListener(e->exportAs());
        ct.addActionListener(e->closeTab(tabbedPane.getSelectedIndex())); ex.addActionListener(e->confirmExit());
        m.add(n);m.add(o);m.addSeparator();m.add(s);m.add(sa);m.add(dup);m.addSeparator();m.add(exp);m.addSeparator();m.add(ct);m.addSeparator();m.add(ex); return m;
    }

    private JMenu buildEditMenu() {
        JMenu m=styledMenu("Edit");
        JMenuItem u=si("Undo"); u.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK));
        JMenuItem r=si("Redo"); r.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK));
        JMenuItem x=si("Cut"); x.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X,InputEvent.CTRL_DOWN_MASK));
        JMenuItem c=si("Copy"); c.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,InputEvent.CTRL_DOWN_MASK));
        JMenuItem v=si("Paste"); v.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V,InputEvent.CTRL_DOWN_MASK));
        JMenuItem a=si("Select All"); a.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK));
        JMenuItem f=si("Find & Replace..."); f.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK));
        JMenuItem sn=si("Select Next Occurrence"); sn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,InputEvent.CTRL_DOWN_MASK));
        JMenuItem ra=si("Replace All Occurrences...");
        u.addActionListener(e->{TabData td=currentTab();if(td!=null&&td.undoManager.canUndo())td.undoManager.undo();});
        r.addActionListener(e->{TabData td=currentTab();if(td!=null&&td.undoManager.canRedo())td.undoManager.redo();});
        x.addActionListener(e->getCurrentTextArea().cut()); c.addActionListener(e->getCurrentTextArea().copy());
        v.addActionListener(e->getCurrentTextArea().paste()); a.addActionListener(e->getCurrentTextArea().selectAll());
        f.addActionListener(e->openFindReplace()); sn.addActionListener(e->selectNextOccurrence()); ra.addActionListener(e->replaceAllOccurrences());
        // Read-only + Sub/Superscript
        JCheckBoxMenuItem ro=new JCheckBoxMenuItem("Read-Only Mode",false); sci(ro);
        ro.addActionListener(e->toggleReadOnly(ro.isSelected()));
        JMenuItem sub=si("Insert Subscript"); JMenuItem sup=si("Insert Superscript");
        sub.addActionListener(e->insertSubscript()); sup.addActionListener(e->insertSuperscript());
        m.add(u);m.add(r);m.addSeparator();m.add(x);m.add(c);m.add(v);m.addSeparator();m.add(a);m.addSeparator();m.add(f);m.add(sn);m.add(ra);
        m.addSeparator();m.add(ro);m.addSeparator();m.add(sub);m.add(sup); return m;
    }

    private JMenu buildViewMenu() {
        JMenu m=styledMenu("View");
        JMenuItem zi=si("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK));
        JMenuItem zo=si("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK));
        JMenuItem zr=si("Reset Zoom"); zr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,InputEvent.CTRL_DOWN_MASK));
        JCheckBoxMenuItem wr=new JCheckBoxMenuItem("Word Wrap",wordWrap); sci(wr);
        JCheckBoxMenuItem ln=new JCheckBoxMenuItem("Line Numbers",showLineNumbers); sci(ln);
        JCheckBoxMenuItem sb=new JCheckBoxMenuItem("Sidebar",sidebarVisible); sci(sb);
        zi.addActionListener(e->changeFontSize(2)); zo.addActionListener(e->changeFontSize(-2)); zr.addActionListener(e->{fontSize=15;applyFontToAll();});
        wr.addActionListener(e->{wordWrap=wr.isSelected();for(TabData td:tabs){td.textArea.setLineWrap(wordWrap);td.textArea.setWrapStyleWord(wordWrap);}});
        ln.addActionListener(e->{showLineNumbers=ln.isSelected();for(TabData td:tabs)td.linePanel.setVisible(showLineNumbers);});
        sb.addActionListener(e->{sidebarVisible=sb.isSelected();splitPane.getLeftComponent().setVisible(sidebarVisible);splitPane.setDividerSize(sidebarVisible?3:0);});
        // Markdown preview
        JCheckBoxMenuItem mp=new JCheckBoxMenuItem("Markdown Preview (Ctrl+M)",false); sci(mp);
        mp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,InputEvent.CTRL_DOWN_MASK));
        mp.addActionListener(e->toggleMarkdownPreview(mp.isSelected()));
        m.add(zi);m.add(zo);m.add(zr);m.addSeparator();m.add(wr);m.add(ln);m.add(sb);m.addSeparator();m.add(mp); return m;
    }

    private JMenu buildFormatMenu() {
        JMenu m=styledMenu("Format");
        JMenuItem ff=si("Font Family..."); JMenuItem fs=si("Font Size..."); JMenuItem fc=si("Font Color..."); JMenuItem hl=si("Highlight Selection");
        ff.addActionListener(e->pickFontFamily()); fs.addActionListener(e->pickFontSize()); fc.addActionListener(e->pickFontColor()); hl.addActionListener(e->highlightSelection());
        m.add(ff);m.add(fs);m.add(fc);m.addSeparator();m.add(hl); return m;
    }

    private JMenu buildInsertMenu() {
        JMenu m=styledMenu("Insert");
        JMenuItem dr=si("Drawing..."); JMenuItem vd=si("View Drawing at Cursor"); JMenuItem tb=si("Table..."); JMenuItem lk=si("Insert Link...");
        dr.addActionListener(e->openDrawingPad()); vd.addActionListener(e->viewDrawingAtCursor()); tb.addActionListener(e->insertTable()); lk.addActionListener(e->insertLink());
        m.add(dr);m.add(vd);m.addSeparator();m.add(tb);m.addSeparator();m.add(lk); return m;
    }

    private JMenu buildToolsMenu() {
        JMenu m=styledMenu("Tools");
        JCheckBoxMenuItem as=new JCheckBoxMenuItem("Auto-Save (every 10s)",autoSaveEnabled); sci(as);
        as.addActionListener(e->{autoSaveEnabled=as.isSelected();flashStatus(autoSaveEnabled?"Auto-save ON":"Auto-save OFF");});
        JMenuItem p=si("Pin/Unpin Note"); JMenuItem v=si("Version History..."); JMenuItem sd=si("Self-Destruct Timer...");
        JMenuItem tt=si("Typing Speed Test...");
        JMenuItem diary=si("Diary Mode...");
        p.addActionListener(e->togglePin()); v.addActionListener(e->showVersionHistory()); sd.addActionListener(e->setSelfDestruct());
        tt.addActionListener(e->openTypingTest()); diary.addActionListener(e->openDiary());
        JMenuItem dr=si("Reset Diary...");
        dr.addActionListener(e->resetDiary());
        m.add(as);m.addSeparator();m.add(p);m.add(v);m.addSeparator();m.add(tt);m.add(diary);m.add(dr);m.addSeparator();m.add(sd); return m;
    }

    private JMenu buildThemeMenu() {
        JMenu m=styledMenu("Theme"); ButtonGroup g=new ButtonGroup();
        String[][] ts={{"Ink (dark)","ink"},{"Parchment (light)","par"},{"Mocha (warm)","moc"},{"Ocean (blue)","oce"},{"Sunset (warm)","sun"},{"Forest (green)","for"},
            {"Lavender (soft)","lav"},{"Dracula (classic)","dra"},{"Nord (arctic)","nor"},{"Solarized (dark)","sol"},{"CRT Terminal","crt"}};
        boolean first=true;
        for(String[] t:ts){ JRadioButtonMenuItem i=new JRadioButtonMenuItem(t[0],first); sri(i); g.add(i);
            final String k=t[1]; i.addActionListener(e->{
                switch(k){case"ink":applyTheme(new InkTheme());break;case"par":applyTheme(new ParchmentTheme());break;
                case"moc":applyTheme(new MochaTheme());break;case"oce":applyTheme(new OceanTheme());break;
                case"sun":applyTheme(new SunsetTheme());break;case"for":applyTheme(new ForestTheme());break;
                case"lav":applyTheme(new LavenderTheme());break;case"dra":applyTheme(new DraculaTheme());break;
                case"nor":applyTheme(new NordTheme());break;case"sol":applyTheme(new SolarizedTheme());break;
                case"crt":applyTheme(new CRTTheme());break;}
            });
            m.add(i); first=false; } return m;
    }

    private JMenu buildSettingsMenu() {
        JMenu m=styledMenu("Settings");
        // Auto-save
        JCheckBoxMenuItem as=new JCheckBoxMenuItem("Auto-Save (every 10s)",autoSaveEnabled); sci(as);
        as.addActionListener(e->{autoSaveEnabled=as.isSelected();flashStatus(autoSaveEnabled?"Auto-save ON":"Auto-save OFF");});
        // Clock format
        JCheckBoxMenuItem ck=new JCheckBoxMenuItem("24-Hour Clock",use24HourClock); sci(ck);
        ck.addActionListener(e->{use24HourClock=ck.isSelected();flashStatus(use24HourClock?"24h clock":"12h clock");});
        // Dyslexia mode
        JMenuItem dx=si("Dyslexia Mode Settings...");
        dx.addActionListener(e->{if(dyslexiaMode.showSettingsDialog(this)){applyDyslexiaMode();flashStatus(dyslexiaMode.enabled?"Dyslexia mode ON":"Dyslexia mode OFF");}});
        // Fullscreen
        JCheckBoxMenuItem fs=new JCheckBoxMenuItem("Fullscreen (F11)",false); sci(fs);
        fs.addActionListener(e->toggleFullScreen());
        m.add(as);m.add(ck);m.addSeparator();m.add(fs);m.addSeparator();m.add(dx); return m;
    }

    private JMenu buildHelpMenu() {
        JMenu m=styledMenu("Help"); JMenuItem sc=si("Keyboard Shortcuts"); JMenuItem ab=si("About");
        sc.addActionListener(e->showShortcuts()); ab.addActionListener(e->showAbout());
        m.add(sc);m.addSeparator();m.add(ab); return m;
    }

    private void initStatusBar() {
        statusBar=new JPanel(new BorderLayout()); statusBar.setBackground(currentTheme.getStatusBg());
        statusBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0,currentTheme.getBorder()));
        statusBar.setPreferredSize(new Dimension(0,28));
        Font sf=new Font("Consolas",Font.PLAIN,11);
        Color sfg=new Color(currentTheme.getForeground().getRed(),currentTheme.getForeground().getGreen(),currentTheme.getForeground().getBlue(),160);
        statusLeft=new JLabel("  Ln 1, Col 1"); statusMid=new JLabel("0 words",JLabel.CENTER);
        statusRight=new JLabel("v"+VERSION+"  ");
        lastEditedLabel=new JLabel("  ");
        lastEditedLabel.setFont(sf); lastEditedLabel.setForeground(sfg);
        for(JLabel l:new JLabel[]{statusLeft,statusMid,statusRight}){l.setFont(sf);l.setForeground(sfg);}
        JPanel rightPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,2)); rightPanel.setOpaque(false);
        rightPanel.add(lastEditedLabel); rightPanel.add(statusRight);
        statusBar.add(statusLeft,BorderLayout.WEST); statusBar.add(statusMid,BorderLayout.CENTER); statusBar.add(rightPanel,BorderLayout.EAST);
        getContentPane().add(statusBar,BorderLayout.SOUTH);
    }

    private void initAutoSave() {
        Runnable task=()->{while(running){try{Thread.sleep(10000);synchronized(this){if(autoSaveEnabled){for(TabData td:tabs){if(td.modified&&td.file!=null){saveTabFile(td);flashStatus("Auto-saved");}}}}}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}};
        autoSaveThread=new Thread(task,"AutoSave"); autoSaveThread.setDaemon(true); autoSaveThread.setPriority(Thread.MIN_PRIORITY); autoSaveThread.start();
    }

    private void initWindowEvents() {
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){confirmExit();}});
        // F11 fullscreen toggle & ESC to exit fullscreen
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11,0),"toggleFS");
        getRootPane().getActionMap().put("toggleFS",new AbstractAction(){public void actionPerformed(ActionEvent e){toggleFullScreen();}});
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),"exitFS");
        getRootPane().getActionMap().put("exitFS",new AbstractAction(){public void actionPerformed(ActionEvent e){if(isFullScreen)toggleFullScreen();}});
    }

    private void openFile() {
        JFileChooser ch=new JFileChooser(); ch.setDialogTitle("Open"); ch.setAcceptAllFileFilterUsed(true);
        ch.addChoosableFileFilter(new FileNameExtensionFilter("Text (*.txt)","txt"));
        ch.addChoosableFileFilter(new FileNameExtensionFilter("Java (*.java)","java"));
        ch.addChoosableFileFilter(new FileNameExtensionFilter("Python (*.py)","py"));
        if(ch.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)loadFileIntoTab(ch.getSelectedFile());
    }

    private void loadFileIntoTab(File file) {
        StringBuilder sb=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new FileReader(file))){String l;while((l=br.readLine())!=null)sb.append(l).append("\n");}catch(IOException e){showError("Could not read:\n"+e.getMessage());return;}
        TabData td=addNewTab(file.getName(),file); td.textArea.setText(sb.toString()); td.textArea.setCaretPosition(0); td.lastSaved=sb.toString(); td.modified=false;
        applySyntaxHighlighting(td);
    }

    private void saveTabFile(TabData td) { if(td.file==null){saveFileAs();return;} writeToFile(td,td.file); }

    private void saveFileAs() {
        TabData td=currentTab(); if(td==null)return;
        SaveDialog dlg=new SaveDialog(this,currentTheme,td.file);
        dlg.setVisible(true);
        if(dlg.isApproved()){
            File f=dlg.getSelectedFile();
            writeToFile(td,f);
        }
    }

    private void writeToFile(TabData td, File file) {
        try(BufferedWriter w=new BufferedWriter(new FileWriter(file))){
            w.write(td.textArea.getText());
            if(td.versionHistory.size()>=5)td.versionHistory.remove(0);
            td.versionHistory.add(td.textArea.getText());
            td.file=file; td.lastSaved=td.textArea.getText(); td.modified=false;
            int i=tabs.indexOf(td); if(i>=0)updateTabTitle(i,file.getName());
            setTitle(APP_NAME+"  ·  "+file.getName()); flashStatus("Saved");
        }catch(IOException e){showError("Failed to save:\n"+e.getMessage());}
    }

    private void updateTabTitle(int i,String t){Component c=tabbedPane.getTabComponentAt(i);if(c instanceof JPanel){JPanel p=(JPanel)c;if(p.getComponentCount()>0&&p.getComponent(0) instanceof JLabel)((JLabel)p.getComponent(0)).setText(t);}}

    private void pickFontFamily() {
        String[] fonts=GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        String p=(String)JOptionPane.showInputDialog(this,"Choose Font:","Font Family",JOptionPane.PLAIN_MESSAGE,null,fonts,fontFamily);
        if(p!=null){fontFamily=p;applyFontToAll();}
    }

    private void pickFontSize() {
        String v=JOptionPane.showInputDialog(this,"Font Size (9-72):",fontSize);
        if(v!=null){try{int s=Integer.parseInt(v);if(s>=9&&s<=72){fontSize=s;applyFontToAll();}}catch(NumberFormatException ignored){}}
    }

    private void pickFontColor() {
        Color c=JColorChooser.showDialog(this,"Font Color",fontColor!=null?fontColor:currentTheme.getForeground());
        if(c!=null){fontColor=c;for(TabData td:tabs)td.textArea.setForeground(c);}
    }

    private void highlightSelection() {
        TabData td=currentTab(); if(td==null)return;
        Color c=JColorChooser.showDialog(this,"Highlight Color",Color.YELLOW); if(c==null)return;
        Highlighter hl=td.textArea.getHighlighter();
        try{int s=td.textArea.getSelectionStart(),e=td.textArea.getSelectionEnd();if(s!=e)hl.addHighlight(s,e,new DefaultHighlighter.DefaultHighlightPainter(c));}catch(BadLocationException ignored){}
    }

    private void applyFontToAll() { for(TabData td:tabs){td.textArea.setFont(new Font(fontFamily,Font.PLAIN,fontSize));td.linePanel.repaint();} statusRight.setText("  "+fontSize+"pt  ·  v"+VERSION+"  "); }

    private void applySyntaxHighlighting(TabData td) {
        if(td.file==null)return; String name=td.file.getName().toLowerCase(); Highlighter hl=td.textArea.getHighlighter(); String text=td.textArea.getText();
        String[] kw=null; Color kwc=currentTheme.getAccent();
        if(name.endsWith(".java"))kw=new String[]{"public","private","protected","class","interface","extends","implements","static","final","void","int","String","boolean","return","new","if","else","for","while","try","catch","import","package"};
        else if(name.endsWith(".py"))kw=new String[]{"def","class","import","from","return","if","elif","else","for","while","try","except","with","as","in","not","and","or","True","False","None","self","print"};
        else if(name.endsWith(".js"))kw=new String[]{"function","const","let","var","return","if","else","for","while","class","import","export","from","new","this","async","await","try","catch"};
        if(kw==null)return;
        for(String k:kw){int idx=0;while((idx=text.indexOf(k,idx))>=0){boolean vs=(idx==0||!Character.isLetterOrDigit(text.charAt(idx-1)));
            boolean ve=(idx+k.length()>=text.length()||!Character.isLetterOrDigit(text.charAt(idx+k.length())));
            if(vs&&ve){try{hl.addHighlight(idx,idx+k.length(),new DefaultHighlighter.DefaultHighlightPainter(new Color(kwc.getRed(),kwc.getGreen(),kwc.getBlue(),50)));}catch(BadLocationException ignored){}}
            idx+=k.length();}}
    }

    private void openDrawingPad() {
        File drawDir = new File(notebookDir, "drawings");
        DrawingDialog d = new DrawingDialog(this, currentTheme, drawDir);
        d.setVisible(true);
        File saved = d.getSavedFile();
        if (saved != null) {
            String ref = "[DRAWING: " + saved.getAbsolutePath() + "]";
            getCurrentTextArea().insert(ref + "\n", getCurrentTextArea().getCaretPosition());
            flashStatus("Drawing saved: " + saved.getName());
        }
    }

    private void viewDrawingAtCursor() {
        // Find drawing reference on current line
        try {
            JTextArea ta = getCurrentTextArea();
            int pos = ta.getCaretPosition();
            int lineNum = ta.getLineOfOffset(pos);
            int lineStart = ta.getLineStartOffset(lineNum);
            int lineEnd = ta.getLineEndOffset(lineNum);
            String line = ta.getText(lineStart, lineEnd - lineStart).trim();
            if (line.startsWith("[DRAWING:") && line.endsWith("]")) {
                String path = line.substring(10, line.length() - 1).trim();
                File imgFile = new File(path);
                if (imgFile.exists()) {
                    // Show in a dialog
                    ImageIcon icon = new ImageIcon(imgFile.getAbsolutePath());
                    // Scale if too large
                    Image img = icon.getImage();
                    int w = icon.getIconWidth(), h = icon.getIconHeight();
                    if (w > 800 || h > 600) {
                        double scale = Math.min(800.0/w, 600.0/h);
                        img = img.getScaledInstance((int)(w*scale),(int)(h*scale), Image.SCALE_SMOOTH);
                        icon = new ImageIcon(img);
                    }
                    JLabel imgLabel = new JLabel(icon);
                    JScrollPane sp = new JScrollPane(imgLabel);
                    sp.setPreferredSize(new java.awt.Dimension(Math.min(w+20,820), Math.min(h+20,620)));
                    JOptionPane.showMessageDialog(this, sp, "Drawing: " + imgFile.getName(), JOptionPane.PLAIN_MESSAGE);
                } else {
                    showError("Drawing file not found:\n" + path);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Place your cursor on a line containing a [DRAWING: ...] reference.", "No Drawing Found", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (BadLocationException ex) {
            showError("Could not read current line.");
        }
    }

    private void insertTable() {
        JPanel p=new JPanel(new GridLayout(2,2,8,8)); JSpinner rows=new JSpinner(new SpinnerNumberModel(3,1,50,1)); JSpinner cols=new JSpinner(new SpinnerNumberModel(3,1,20,1));
        p.add(new JLabel("Rows:")); p.add(rows); p.add(new JLabel("Columns:")); p.add(cols);
        if(JOptionPane.showConfirmDialog(this,p,"Insert Table",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
            int r=(int)rows.getValue(),c=(int)cols.getValue(); StringBuilder sb=new StringBuilder("\n");
            sb.append("|"); for(int i=0;i<c;i++)sb.append(" Header").append(i+1).append(" |"); sb.append("\n");
            sb.append("|"); for(int i=0;i<c;i++)sb.append("---------|"); sb.append("\n");
            for(int i=0;i<r;i++){sb.append("|");for(int j=0;j<c;j++)sb.append("         |");sb.append("\n");}
            getCurrentTextArea().insert(sb.toString(),getCurrentTextArea().getCaretPosition());}
    }

    private void togglePin() {
        TabData td=currentTab(); if(td==null)return; td.pinned=!td.pinned;
        int i=tabs.indexOf(td); String t=td.file!=null?td.file.getName():"untitled";
        updateTabTitle(i,(td.pinned?"[PIN] ":"")+t); flashStatus(td.pinned?"Pinned":"Unpinned");
    }

    private void showVersionHistory() {
        TabData td=currentTab(); if(td==null)return;
        if(td.versionHistory.isEmpty()){JOptionPane.showMessageDialog(this,"No version history yet.\nSave to create snapshots.");return;}
        String[] labels=new String[td.versionHistory.size()];
        for(int i=0;i<labels.length;i++)labels[i]="Version "+(i+1);
        String picked=(String)JOptionPane.showInputDialog(this,"Restore version:","Version History",JOptionPane.PLAIN_MESSAGE,null,labels,labels[labels.length-1]);
        if(picked!=null){int i=java.util.Arrays.asList(labels).indexOf(picked);if(i>=0){td.textArea.setText(td.versionHistory.get(i));flashStatus("Restored v"+(i+1));}}
    }

    private void setSelfDestruct() {
        TabData td=currentTab(); if(td==null)return;
        JPanel panel=new JPanel(new GridLayout(3,1,4,4));
        panel.add(new JLabel("Choose preset or enter custom minutes:"));
        JPanel presets=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
        JTextField customField=new JTextField("5",6);
        String[] opts={"5","15","30","60"};
        for(String o:opts){JButton b=new JButton(o+"m");b.addActionListener(e->customField.setText(o));presets.add(b);}
        JButton cancelBtn=new JButton("Cancel Timer");cancelBtn.addActionListener(e->customField.setText("0"));presets.add(cancelBtn);
        panel.add(presets); panel.add(customField);
        int r=JOptionPane.showConfirmDialog(this,panel,"Self-Destruct Timer",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        if(r!=JOptionPane.OK_OPTION)return;
        if(td.selfDestructTimer!=null)td.selfDestructTimer.stop();
        int mins;
        try{mins=Integer.parseInt(customField.getText().trim());}catch(NumberFormatException e){showError("Invalid number");return;}
        if(mins<=0){td.selfDestructTime=0;flashStatus("Timer cancelled");return;}
        td.selfDestructTime=System.currentTimeMillis()+mins*60000L;
        td.selfDestructTimer=new javax.swing.Timer(1000,e->{long rem=td.selfDestructTime-System.currentTimeMillis();
            if(rem<=0){((javax.swing.Timer)e.getSource()).stop();td.textArea.setText("");if(td.file!=null&&td.file.exists())td.file.delete();td.file=null;td.modified=false;
                JOptionPane.showMessageDialog(NotebookMe.this,"Note has self-destructed!","Destroyed",JOptionPane.WARNING_MESSAGE);
                int i=tabs.indexOf(td);if(i>=0)updateTabTitle(i,"[destroyed]");}
            else{long s=rem/1000;statusRight.setText(String.format("  DESTRUCT %d:%02d  ",s/60,s%60));}});
        td.selfDestructTimer.start(); flashStatus("Self-destruct in "+mins+"min");
    }

    private void loadFolderTree() {
        rootNode.removeAllChildren(); File[] folders=notebookDir.listFiles(File::isDirectory);
        if(folders!=null){for(File f:folders){DefaultMutableTreeNode fn=new DefaultMutableTreeNode(f.getName());
            File[] notes=f.listFiles(fi->fi.isFile()&&fi.getName().endsWith(".txt"));
            if(notes!=null)for(File n:notes)fn.add(new DefaultMutableTreeNode(n.getName()));rootNode.add(fn);}}
        treeModel.reload(); for(int i=0;i<folderTree.getRowCount();i++)folderTree.expandRow(i);
    }

    private void createFolder() {
        String name=JOptionPane.showInputDialog(this,"Folder name:"); if(name==null||name.isBlank())return;
        new File(notebookDir,name).mkdirs(); loadFolderTree(); flashStatus("Folder: "+name);
    }

    private void createNoteInFolder() {
        TreePath path=folderTree.getSelectionPath(); if(path==null||path.getPathCount()<2){JOptionPane.showMessageDialog(this,"Select a folder first.");return;}
        String folder=path.getPathComponent(1).toString(); String name=JOptionPane.showInputDialog(this,"Note name:");
        if(name==null||name.isBlank())return; if(!name.endsWith(".txt"))name+=".txt";
        File f=new File(new File(notebookDir,folder),name); try{f.createNewFile();}catch(IOException ex){showError("Cannot create note.");return;}
        loadFolderTree(); loadFileIntoTab(f);
    }

    private void openFromTree() {
        TreePath path=folderTree.getSelectionPath(); if(path==null||path.getPathCount()<3)return;
        String folder=path.getPathComponent(1).toString(); String note=path.getPathComponent(2).toString();
        loadFileIntoTab(new File(new File(notebookDir,folder),note));
    }

    private void deleteFromTree() {
        TreePath path=folderTree.getSelectionPath(); if(path==null||path.getPathCount()<2)return;
        if(JOptionPane.showConfirmDialog(this,"Delete?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        if(path.getPathCount()==2){String fn=path.getPathComponent(1).toString();File dir=new File(notebookDir,fn);File[] fs=dir.listFiles();if(fs!=null)for(File f:fs)f.delete();dir.delete();}
        else{String folder=path.getPathComponent(1).toString();String note=path.getPathComponent(2).toString();new File(new File(notebookDir,folder),note).delete();}
        loadFolderTree();
    }

    private void quickSearch() {
        String q=searchField.getText(); if(q.isEmpty()||q.equals("Search..."))return; JTextArea ta=getCurrentTextArea();
        String text=ta.getText().toLowerCase(); int from=ta.getCaretPosition(); int idx=text.indexOf(q.toLowerCase(),from);
        if(idx==-1)idx=text.indexOf(q.toLowerCase(),0);
        if(idx>=0){ta.setCaretPosition(idx);ta.select(idx,idx+q.length());ta.requestFocus();flashStatus("Found");}else flashStatus("Not found");
    }

    private void onTextChange(TabData td) {
        td.modified=true; int i=tabs.indexOf(td); if(i>=0){String n=td.file!=null?td.file.getName():"untitled"; updateTabTitle(i,"* "+n);}
        updateStatus();
        // Update last-edited timestamp with clock format
        String fmt=use24HourClock?"HH:mm:ss":"hh:mm:ss a";
        SwingUtilities.invokeLater(()->lastEditedLabel.setText("Last edited: "+new SimpleDateFormat(fmt).format(new java.util.Date())+"  "));
    }

    private void updateStatus() { SwingUtilities.invokeLater(()->{JTextArea ta=getCurrentTextArea();String t=ta.getText();int ch=t.length(),w=t.isBlank()?0:t.trim().split("\\s+").length,l=ta.getLineCount();statusMid.setText(w+" words  ·  "+ch+" chars  ·  "+l+" lines");}); }

    private void updateCaretStatus() { SwingUtilities.invokeLater(()->{try{JTextArea ta=getCurrentTextArea();int pos=ta.getCaretPosition(),line=ta.getLineOfOffset(pos)+1,col=pos-ta.getLineStartOffset(line-1)+1;statusLeft.setText("  Ln "+line+", Col "+col);}catch(BadLocationException ignored){}}); }

    private void changeFontSize(int d) { fontSize=Math.max(9,Math.min(72,fontSize+d)); applyFontToAll(); }

    private void applyTheme(Theme theme) {
        currentTheme=theme;
        applyThemeUI();
        // CRT scanline overlay
        if(scanlineOverlay!=null){getLayeredPane().remove(scanlineOverlay);scanlineOverlay=null;}
        if(currentTheme.hasScanlines()){
            scanlineOverlay=new JPanel(){@Override protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g;
                g2.setColor(new Color(0,0,0,30));
                for(int y=0;y<getHeight();y+=3) g2.fillRect(0,y,getWidth(),1);
                // CRT glow at edges
                g2.setColor(new Color(0,255,65,8));
                g2.fillRect(0,0,getWidth(),2);
                g2.fillRect(0,getHeight()-2,getWidth(),2);
            }};
            scanlineOverlay.setOpaque(false);
            scanlineOverlay.setBounds(0,0,getWidth(),getHeight());
            getLayeredPane().add(scanlineOverlay,JLayeredPane.PALETTE_LAYER);
            addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
                if(scanlineOverlay!=null)scanlineOverlay.setBounds(0,0,getWidth(),getHeight());
            }});
        }
    }

    /** Smooth slide animation for sidebar toggle */
    private void animateSidebar() {
        final int targetWidth = 210;
        final int step = 15;
        final int delay = 12; // ms per frame
        if (sidebarVisible) {
            // Slide closed
            javax.swing.Timer timer = new javax.swing.Timer(delay, null);
            timer.addActionListener(e -> {
                int current = splitPane.getDividerLocation();
                if (current > step) {
                    splitPane.setDividerLocation(current - step);
                } else {
                    splitPane.setDividerLocation(0);
                    splitPane.getLeftComponent().setVisible(false);
                    splitPane.setDividerSize(0);
                    sidebarVisible = false;
                    sideToggleBtn.repaint();
                    timer.stop();
                }
            });
            timer.start();
        } else {
            // Slide open
            splitPane.getLeftComponent().setVisible(true);
            splitPane.setDividerSize(3);
            splitPane.setDividerLocation(0);
            javax.swing.Timer timer = new javax.swing.Timer(delay, null);
            timer.addActionListener(e -> {
                int current = splitPane.getDividerLocation();
                if (current < targetWidth - step) {
                    splitPane.setDividerLocation(current + step);
                } else {
                    splitPane.setDividerLocation(targetWidth);
                    sidebarVisible = true;
                    sideToggleBtn.repaint();
                    timer.stop();
                }
            });
            timer.start();
        }
    }

    /** Toggle fullscreen mode — smooth via maximized state */
    private void toggleFullScreen() {
        if (!isFullScreen) {
            dispose();
            setUndecorated(true);
            setVisible(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            isFullScreen = true;
            flashStatus("Fullscreen — press ESC or F11 to exit");
        } else {
            dispose();
            setUndecorated(false);
            setExtendedState(JFrame.NORMAL);
            setSize(1100, 750);
            setVisible(true);
            setLocationRelativeTo(null);
            isFullScreen = false;
            flashStatus("Windowed mode");
        }
    }

    private void applyThemeUI() {
        Color bg=currentTheme.getBackground(),fg=currentTheme.getForeground(),ac=currentTheme.getAccent();
        for(TabData td:tabs){td.textArea.setBackground(bg);td.textArea.setForeground(fontColor!=null?fontColor:fg);td.textArea.setCaretColor(ac);td.textArea.setSelectionColor(ac.darker());td.textArea.setSelectedTextColor(bg);td.linePanel.setTheme(currentTheme);}
        getContentPane().setBackground(bg); menuBar.setBackground(currentTheme.getMenuBg()); menuBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,currentTheme.getBorder()));
        // Force menu text to black for visibility
        for(int mi=0;mi<menuBar.getMenuCount();mi++){JMenu menu=menuBar.getMenu(mi);if(menu!=null){menu.setForeground(Color.BLACK);}}
        statusBar.setBackground(currentTheme.getStatusBg()); statusBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0,currentTheme.getBorder()));
        Color sfg=new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),160);
        statusLeft.setForeground(sfg);statusMid.setForeground(sfg);statusRight.setForeground(sfg);lastEditedLabel.setForeground(sfg);
        tabbedPane.setBackground(currentTheme.getMenuBg());tabbedPane.setForeground(fg);
        // Update all tab header labels and close buttons to match new theme
        for(int i=0;i<tabbedPane.getTabCount();i++){
            Component c=tabbedPane.getTabComponentAt(i);
            if(c instanceof JPanel){
                JPanel p=(JPanel)c;
                for(Component child:p.getComponents()){
                    if(child instanceof JLabel)((JLabel)child).setForeground(Color.BLACK);
                    if(child instanceof JButton)((JButton)child).setForeground(Color.BLACK);
                }
            }
        }
        // Update sidebar tree colors
        folderTree.setBackground(currentTheme.getMenuBg());
        folderTree.setForeground(fg);
        repaint();
    }

    private void confirmExit() { for(TabData td:tabs){if(td.modified){int r=JOptionPane.showConfirmDialog(this,"Unsaved changes. Exit?","Exit",JOptionPane.YES_NO_OPTION);if(r!=JOptionPane.YES_OPTION)return;break;}} running=false;autoSaveThread.interrupt();dispose();System.exit(0); }
    private void openFindReplace() { new FindReplaceDialog(this,getCurrentTextArea(),currentTheme).setVisible(true); }
    private void flashStatus(String msg) { SwingUtilities.invokeLater(()->statusRight.setText("  "+msg+"  ")); Thread t=new Thread(()->{try{Thread.sleep(2500);}catch(InterruptedException ignored){}SwingUtilities.invokeLater(()->statusRight.setText("  v"+VERSION+"  "));}); t.setDaemon(true);t.start(); }
    private void showError(String msg) { JOptionPane.showMessageDialog(this,msg,"Error",JOptionPane.ERROR_MESSAGE); }

    // ── New v3.0 methods ──
    private void duplicateTab() {
        TabData src=currentTab(); if(src==null)return;
        String name=src.file!=null?src.file.getName()+" (copy)":"untitled (copy)";
        TabData dup=addNewTab(name,null);
        dup.textArea.setText(src.textArea.getText());
        dup.textArea.setCaretPosition(0);
    }

    private void exportToPDF() {
        TabData td=currentTab(); if(td==null)return;
        JFileChooser ch=new JFileChooser(); ch.setDialogTitle("Export to PDF");
        ch.setSelectedFile(new File(td.file!=null?td.file.getName().replace(".","_")+".pdf":"untitled.pdf"));
        if(ch.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File out=ch.getSelectedFile();
        if(!out.getName().endsWith(".pdf"))out=new File(out.getAbsolutePath()+".pdf");
        // Write raw PDF
        String text=td.textArea.getText();
        String[] lines=text.split("\n");
        try(PrintWriter pw=new PrintWriter(new FileWriter(out))){
            pw.println("%PDF-1.4");
            pw.println("1 0 obj <</Type /Catalog /Pages 2 0 R>> endobj");
            pw.println("2 0 obj <</Type /Pages /Kids [3 0 R] /Count 1>> endobj");
            // Build content stream
            StringBuilder content=new StringBuilder();
            content.append("BT /F1 11 Tf 50 750 Td 14 TL ");
            for(String line:lines){
                String escaped=line.replace("\\","\\\\").replace("(","\\(").replace(")","\\)");
                content.append("(").append(escaped).append(") Tj T* ");
            }
            content.append("ET");
            String cs=content.toString();
            pw.println("3 0 obj <</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources <</Font <</F1 5 0 R>>>>>> endobj");
            pw.println("4 0 obj <</Length "+cs.length()+">>\nstream");
            pw.println(cs);
            pw.println("endstream endobj");
            pw.println("5 0 obj <</Type /Font /Subtype /Type1 /BaseFont /Courier>> endobj");
            pw.println("xref\n0 6");
            pw.println("trailer <</Size 6 /Root 1 0 R>>");
            pw.println("%%EOF");
            flashStatus("Exported: "+out.getName());
        }catch(IOException e){showError("PDF export failed:\n"+e.getMessage());}
    }

    private void openTypingTest() { new TypingTestDialog(this,currentTheme).setVisible(true); }
    private void openDiary() { new DiaryDialog(this,currentTheme,notebookDir).setVisible(true); }

    // ── v4.0 methods ──
    private void selectNextOccurrence() {
        JTextArea ta=getCurrentTextArea(); String sel=ta.getSelectedText();
        if(sel==null||sel.isEmpty()){flashStatus("Select text first (Ctrl+D)");return;}
        String text=ta.getText();
        Highlighter hl=ta.getHighlighter();
        // Highlight ALL occurrences at once (like VS Code Ctrl+Shift+L)
        int count=0; int idx=0;
        Color accentAlpha=new Color(currentTheme.getAccent().getRed(),currentTheme.getAccent().getGreen(),currentTheme.getAccent().getBlue(),80);
        while((idx=text.indexOf(sel,idx))>=0){
            try{hl.addHighlight(idx,idx+sel.length(),new DefaultHighlighter.DefaultHighlightPainter(accentAlpha));}catch(BadLocationException ignored){}
            count++; idx+=sel.length();
        }
        if(count>0) flashStatus(count+" occurrences highlighted");
        else flashStatus("No occurrences found");
    }

    private void replaceAllOccurrences() {
        JTextArea ta=getCurrentTextArea(); String sel=ta.getSelectedText();
        if(sel==null||sel.isEmpty()){flashStatus("Select text to replace");return;}
        String rep=JOptionPane.showInputDialog(this,"Replace all \""+sel+"\" with:","Replace All",JOptionPane.QUESTION_MESSAGE);
        if(rep==null)return;
        String txt=ta.getText(); int count=0;
        String newTxt=txt; while(newTxt.contains(sel)){newTxt=newTxt.replaceFirst(java.util.regex.Pattern.quote(sel),java.util.regex.Matcher.quoteReplacement(rep));count++;}
        ta.setText(newTxt); flashStatus("Replaced "+count+" occurrences");
    }

    private void insertLink() {
        JTextField urlField=new JTextField(25); JTextField textField=new JTextField(25);
        JPanel p=new JPanel(new GridLayout(4,1,4,4));
        p.add(new JLabel("Display text:")); p.add(textField);
        p.add(new JLabel("URL:")); p.add(urlField);
        int r=JOptionPane.showConfirmDialog(this,p,"Insert Link",JOptionPane.OK_CANCEL_OPTION);
        if(r!=JOptionPane.OK_OPTION)return;
        String url=urlField.getText().trim(); String text=textField.getText().trim();
        if(url.isEmpty()){showError("URL is required");return;}
        if(text.isEmpty()) text=url;
        getCurrentTextArea().insert("["+text+"]("+url+")",getCurrentTextArea().getCaretPosition());
        flashStatus("Link inserted");
    }

    private void applyDyslexiaMode() {
        for(TabData td:tabs){
            if(dyslexiaMode.enabled){
                dyslexiaMode.applyTo(td.textArea,fontSize);
            } else {
                // Restore normal theme
                td.textArea.setFont(new Font(fontFamily,Font.PLAIN,fontSize));
                td.textArea.setBackground(currentTheme.getBackground());
                td.textArea.setForeground(fontColor!=null?fontColor:currentTheme.getForeground());
                td.textArea.setCaretColor(currentTheme.getAccent());
                td.textArea.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
            }
        }
    }

    // ── v5.0.0 methods ──

    private void exportAs() {
        TabData td = currentTab(); if (td == null) return;
        String[] formats = {"Text (.txt)", "Markdown (.md)", "PDF (.pdf)", "Word Document (.docx)"};
        String choice = (String) JOptionPane.showInputDialog(this, "Export format:", "Export As",
            JOptionPane.PLAIN_MESSAGE, null, formats, formats[0]);
        if (choice == null) return;
        SaveDialog dlg = new SaveDialog(this, currentTheme, td.file);
        dlg.setVisible(true);
        if (!dlg.isApproved()) return;
        File f = dlg.getSelectedFile();
        String text = td.textArea.getText();
        try {
            if (choice.contains(".txt") || choice.contains(".md")) {
                try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) { w.write(text); }
            } else if (choice.contains(".pdf")) {
                exportToPDFFile(f, text);
            } else if (choice.contains(".docx")) {
                exportToDocx(f, text);
            }
            flashStatus("Exported: " + f.getName());
        } catch (IOException e) { showError("Export failed:\n" + e.getMessage()); }
    }

    private void exportToPDFFile(File out, String text) throws IOException {
        String[] lines = text.split("\n");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("%PDF-1.4");
            pw.println("1 0 obj <</Type /Catalog /Pages 2 0 R>> endobj");
            pw.println("2 0 obj <</Type /Pages /Kids [3 0 R] /Count 1>> endobj");
            StringBuilder content = new StringBuilder();
            content.append("BT /F1 11 Tf 50 750 Td 14 TL ");
            for (String line : lines) {
                String escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
                content.append("(").append(escaped).append(") Tj T* ");
            }
            content.append("ET");
            String cs = content.toString();
            pw.println("3 0 obj <</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources <</Font <</F1 5 0 R>>>>>> endobj");
            pw.println("4 0 obj <</Length " + cs.length() + ">>\nstream");
            pw.println(cs);
            pw.println("endstream endobj");
            pw.println("5 0 obj <</Type /Font /Subtype /Type1 /BaseFont /Courier>> endobj");
            pw.println("xref\n0 6");
            pw.println("trailer <</Size 6 /Root 1 0 R>>");
            pw.println("%%EOF");
        }
    }

    private void exportToDocx(File out, String text) throws IOException {
        // Minimal OOXML docx = ZIP with XML content
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(out))) {
            // [Content_Types].xml
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>").getBytes("UTF-8"));
            zos.closeEntry();
            // _rels/.rels
            zos.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>").getBytes("UTF-8"));
            zos.closeEntry();
            // word/_rels/document.xml.rels
            zos.putNextEntry(new java.util.zip.ZipEntry("word/_rels/document.xml.rels"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>").getBytes("UTF-8"));
            zos.closeEntry();
            // word/document.xml
            StringBuilder docXml = new StringBuilder();
            docXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            docXml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
            docXml.append("<w:body>");
            for (String line : text.split("\n")) {
                String esc = line.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                docXml.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc).append("</w:t></w:r></w:p>");
            }
            docXml.append("</w:body></w:document>");
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write(docXml.toString().getBytes("UTF-8"));
            zos.closeEntry();
        }
    }

    private void toggleReadOnly(boolean enabled) {
        readOnlyMode = enabled;
        for (TabData td : tabs) { td.textArea.setEditable(!readOnlyMode); }
        if (readOnlyMode) {
            statusLeft.setText("  \ud83d\udd12 READ-ONLY");
            flashStatus("Read-only mode ON");
        } else {
            updateCaretStatus();
            flashStatus("Read-only mode OFF");
        }
    }

    private void insertSubscript() {
        JTextArea ta = getCurrentTextArea();
        String sel = ta.getSelectedText();
        if (sel == null || sel.isEmpty()) { sel = JOptionPane.showInputDialog(this, "Text to subscript:"); if (sel == null) return; }
        StringBuilder sb = new StringBuilder();
        for (char c : sel.toCharArray()) { sb.append(toSubscript(c)); }
        ta.replaceSelection(sb.toString());
    }

    private void insertSuperscript() {
        JTextArea ta = getCurrentTextArea();
        String sel = ta.getSelectedText();
        if (sel == null || sel.isEmpty()) { sel = JOptionPane.showInputDialog(this, "Text to superscript:"); if (sel == null) return; }
        StringBuilder sb = new StringBuilder();
        for (char c : sel.toCharArray()) { sb.append(toSuperscript(c)); }
        ta.replaceSelection(sb.toString());
    }

    private char toSubscript(char c) {
        String n = "0123456789"; String sub = "\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089";
        int i = n.indexOf(c); return i >= 0 ? sub.charAt(i) : c;
    }

    private char toSuperscript(char c) {
        String n = "0123456789"; String sup = "\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079";
        int i = n.indexOf(c); return i >= 0 ? sup.charAt(i) : c;
    }

    private void toggleMarkdownPreview(boolean show) {
        mdPreviewVisible = show;
        TabData td = currentTab(); if (td == null) return;
        if (show) {
            mdPreviewPane = new JEditorPane();
            mdPreviewPane.setContentType("text/html");
            mdPreviewPane.setEditable(false);
            mdPreviewPane.setBackground(currentTheme.getBackground());
            String bgCSS = MarkdownRenderer.colorToCSS(currentTheme.getBackground());
            String fgCSS = MarkdownRenderer.colorToCSS(currentTheme.getForeground());
            String acCSS = MarkdownRenderer.colorToCSS(currentTheme.getAccent());
            mdPreviewPane.setText(MarkdownRenderer.toHTML(td.textArea.getText(), bgCSS, fgCSS, acCSS));
            JScrollPane previewScroll = new JScrollPane(mdPreviewPane);
            previewScroll.setBorder(BorderFactory.createMatteBorder(0,1,0,0,currentTheme.getBorder()));
            // Listen for text changes to live-update preview
            td.textArea.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    SwingUtilities.invokeLater(() -> {
                        if (mdPreviewVisible && mdPreviewPane != null) {
                            mdPreviewPane.setText(MarkdownRenderer.toHTML(td.textArea.getText(), bgCSS, fgCSS, acCSS));
                        }
                    });
                }
                public void insertUpdate(DocumentEvent e) { update(); }
                public void removeUpdate(DocumentEvent e) { update(); }
                public void changedUpdate(DocumentEvent e) { update(); }
            });
            // Create split pane for editor + preview
            Container parent = td.editorPanel.getParent();
            if (parent != null) {
                int tabIdx = tabbedPane.indexOfComponent(td.editorPanel);
                if (tabIdx >= 0) {
                    mdSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, td.editorPanel, previewScroll);
                    mdSplitPane.setDividerLocation(getWidth() / 2);
                    mdSplitPane.setDividerSize(3);
                    mdSplitPane.setBorder(null);
                    tabbedPane.setComponentAt(tabIdx, mdSplitPane);
                }
            }
            flashStatus("Markdown preview ON");
        } else {
            // Remove preview, restore just the editor
            if (mdSplitPane != null) {
                int tabIdx = tabbedPane.indexOfComponent(mdSplitPane);
                if (tabIdx >= 0) {
                    tabbedPane.setComponentAt(tabIdx, td.editorPanel);
                }
                mdSplitPane = null;
                mdPreviewPane = null;
            }
            flashStatus("Markdown preview OFF");
        }
    }

    private void resetDiary() {
        int r = JOptionPane.showConfirmDialog(this,
            "This will DELETE all diary entries and reset your PIN.\nThis cannot be undone!\n\nAre you sure?",
            "Reset Diary", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        r = JOptionPane.showConfirmDialog(this,
            "FINAL CONFIRMATION: All diary data will be permanently deleted.",
            "Confirm Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        File diaryDir = new File(notebookDir, "diary");
        if (diaryDir.exists()) {
            File[] files = diaryDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
        File pinFile = new File(notebookDir, "diary_pin.dat");
        if (pinFile.exists()) pinFile.delete();
        flashStatus("Diary reset complete");
    }

    private void showWelcome() {
        getCurrentTextArea().setText("  Welcome to notebook.me v5.0.0\n  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n\n  NEW IN v5.0.0:\n   \ud83d\udcdd Markdown preview (Ctrl+M)\n   \ud83d\udcbe Custom save dialog\n   \ud83d\udce4 Export as .txt .md .pdf .docx\n   \ud83d\udd12 Read-only mode\n   \u2082\u2083 Subscript / Superscript\n   \ud83d\uddd1\ufe0f Reset diary option\n\n  FEATURES:\n   11 themes + CRT Terminal\n   Dyslexia-friendly mode\n   Typing speed test (3 modes)\n   Diary with streak tracker\n   Ctrl+D highlight all occurrences\n   Full screen (F11)\n\n  Start typing to begin.\n");
        getCurrentTextArea().setCaretPosition(0); TabData td=currentTab(); if(td!=null){td.modified=false;td.lastSaved=getCurrentTextArea().getText();}
    }

    private void showShortcuts() { JOptionPane.showMessageDialog(this,"Ctrl+N  New tab\nCtrl+O  Open\nCtrl+S  Save\nCtrl+W  Close tab\nCtrl+Shift+S  Save As\nCtrl+Z  Undo\nCtrl+Y  Redo\nCtrl+F  Find & Replace\nCtrl+D  Highlight All\nCtrl+M  Markdown Preview\nF11  Fullscreen\nESC  Exit fullscreen\nCtrl++/- Zoom","Shortcuts",JOptionPane.INFORMATION_MESSAGE); }
    private void showAbout() { JOptionPane.showMessageDialog(this,"notebook.me v"+VERSION+"\nFeature-rich Java Notepad\n\nMarkdown Preview, Export (.txt .md .pdf .docx),\nRead-Only Mode, Sub/Superscript,\n11 Themes, CRT Terminal, Dyslexia Mode,\nDiary + Streak, Typing Tests, PDF Export,\nFolders, Drawing, Tables & more.\n\nInstances: "+instanceCount,"About",JOptionPane.INFORMATION_MESSAGE); }

    private static final Color DARK_GOLD = new Color(50, 40, 30);
    private JMenu styledMenu(String t){JMenu m=new JMenu(t);m.setForeground(Color.BLACK);m.setFont(new Font("Segoe UI",Font.PLAIN,12));m.getPopupMenu().setBackground(currentTheme.getMenuBg());m.getPopupMenu().setBorder(BorderFactory.createLineBorder(currentTheme.getBorder()));return m;}
    private JMenuItem si(String t){JMenuItem i=new JMenuItem(t);i.setBackground(currentTheme.getMenuBg());i.setForeground(DARK_GOLD);i.setFont(new Font("Segoe UI",Font.PLAIN,12));i.setBorderPainted(false);return i;}
    private void sci(JCheckBoxMenuItem i){i.setBackground(currentTheme.getMenuBg());i.setForeground(DARK_GOLD);i.setFont(new Font("Segoe UI",Font.PLAIN,12));}
    private void sri(JRadioButtonMenuItem i){i.setBackground(currentTheme.getMenuBg());i.setForeground(DARK_GOLD);i.setFont(new Font("Segoe UI",Font.PLAIN,12));}

    // ── Modern Tree Cell Renderer with emoji icons ──
    class ModernTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            String text = value.toString();
            label.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 4));
            // Set icons based on tree depth
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            int depth = node.getLevel();
            if (depth == 0) {
                // Root
                label.setIcon(null);
            } else if (depth == 1) {
                // Folder
                label.setText("📁 " + text);
                label.setIcon(null);
            } else {
                // Note file
                label.setText("📄 " + text);
                label.setIcon(null);
            }
            // Theme colors
            if (sel) {
                label.setBackground(currentTheme.getAccent());
                label.setForeground(currentTheme.getBackground());
                label.setOpaque(true);
            } else {
                label.setBackground(currentTheme.getMenuBg());
                label.setForeground(currentTheme.getForeground());
                label.setOpaque(true);
            }
            return label;
        }
    }

    static class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        private final Theme theme; SlimScrollBarUI(Theme t){this.theme=t;}
        @Override protected void configureScrollBarColors(){thumbColor=theme.getAccent().darker().darker();trackColor=theme.getBackground();}
        @Override protected JButton createDecreaseButton(int o){return zb();}@Override protected JButton createIncreaseButton(int o){return zb();}
        private JButton zb(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(thumbColor);g2.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,6,6);g2.dispose();}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(trackColor);g.fillRect(r.x,r.y,r.width,r.height);}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(NotebookMe::new); }
}
