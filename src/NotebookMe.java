/**
 * notebook.me v6.2 - Feature-rich Java Notepad
 */
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import javax.swing.tree.*;
import javax.swing.undo.*;

public class NotebookMe extends JFrame {
    private static final String APP_NAME = "notebook.me";
    private static final String VERSION  = "6.2";
    private static final int SIDEBAR_WIDTH = 280;
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
    private GradientPanel rootPanel;

    static class TabData {
        JTextArea textArea;
        JPanel editorPanel;
        LineNumberPanel linePanel;
        JScrollPane scrollPane;
        UndoManager undoManager;
        File file;
        boolean modified;
        String lastSaved;
        boolean pinned;
        JPanel tabHeader;
        JLabel tabLabel;
        JButton closeButton;
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
        private final JTextArea textArea;
        private Theme theme;
        private final DocumentListener documentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { queueRefresh(); }
            @Override public void removeUpdate(DocumentEvent e) { queueRefresh(); }
            @Override public void changedUpdate(DocumentEvent e) { queueRefresh(); }
        };

        LineNumberPanel(JTextArea ta, Theme t) {
            this.textArea = ta;
            this.theme = t;
            setOpaque(false);

            textArea.getDocument().addDocumentListener(documentListener);
            textArea.addCaretListener(e -> SwingUtilities.invokeLater(this::repaint));
            textArea.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { queueRefresh(); }
                @Override public void componentMoved(ComponentEvent e) { repaint(); }
            });
            textArea.addPropertyChangeListener("font", e -> queueRefresh());
            textArea.addPropertyChangeListener("document", e -> {
                if (e.getOldValue() instanceof Document) {
                    ((Document) e.getOldValue()).removeDocumentListener(documentListener);
                }
                if (e.getNewValue() instanceof Document) {
                    ((Document) e.getNewValue()).addDocumentListener(documentListener);
                }
                queueRefresh();
            });

            refresh();
        }

        void setTheme(Theme t) {
            this.theme = t;
            repaint();
        }

        void refreshMetrics() {
            refresh();
        }

        private void queueRefresh() {
            SwingUtilities.invokeLater(this::refresh);
        }

        private void refresh() {
            Font gutterFont = ModernUI.monoFont(Font.PLAIN, Math.max(11f, textArea.getFont().getSize2D() - 1f));
            FontMetrics fm = getFontMetrics(gutterFont);
            int digits = Math.max(2, String.valueOf(textArea.getLineCount()).length());
            int width = Math.max(48, fm.charWidth('0') * digits + 24);
            int height = Math.max(textArea.getPreferredSize().height, textArea.getHeight());
            Dimension size = new Dimension(width, height);

            if (!size.equals(getPreferredSize())) {
                setPreferredSize(size);
                revalidate();
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(ModernUI.mix(theme.getMenuBg(), theme.getBackground(), 0.2f));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(ModernUI.hairline(theme));
            g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            Font gutterFont = ModernUI.monoFont(Font.PLAIN, Math.max(11f, textArea.getFont().getSize2D() - 1f));
            g2.setFont(gutterFont);
            FontMetrics fm = g2.getFontMetrics();
            FontMetrics textMetrics = textArea.getFontMetrics(textArea.getFont());
            Element root = textArea.getDocument().getDefaultRootElement();
            int activeLine = -1;
            int startLine = 0;
            int endLine = Math.max(0, root.getElementCount() - 1);
            try {
                activeLine = textArea.getLineOfOffset(textArea.getCaretPosition());
                Rectangle visible = textArea.getVisibleRect();
                int modelX = Math.max(0, textArea.getInsets().left + 1);
                int startOffset = textArea.viewToModel2D(new Point(modelX, visible.y));
                int endOffset = textArea.viewToModel2D(new Point(modelX, visible.y + visible.height));
                startLine = root.getElementIndex(startOffset);
                endLine = root.getElementIndex(endOffset);
            } catch (BadLocationException ignored) {}

            for (int i = startLine; i <= Math.min(endLine, root.getElementCount() - 1); i++) {
                Rectangle2D lineRect;
                try {
                    lineRect = textArea.modelToView2D(root.getElement(i).getStartOffset());
                } catch (BadLocationException ignored) {
                    continue;
                }
                if (lineRect == null) {
                    continue;
                }

                String num = String.valueOf(i + 1);
                boolean active = i == activeLine;
                int boxY = (int) Math.round(lineRect.getY()) + 2;
                int boxHeight = Math.max(16, (int) Math.round(lineRect.getHeight()) - 4);
                if (active) {
                    g2.setColor(ModernUI.withAlpha(theme.getAccent(), 105));
                    g2.fillRoundRect(6, boxY, getWidth() - 12, boxHeight, 8, 8);
                    g2.setColor(ModernUI.contrastText(theme.getAccent()));
                } else {
                    g2.setColor(ModernUI.withAlpha(theme.getForeground(), 110));
                }
                int x = getWidth() - fm.stringWidth(num) - 10;
                int y = (int) Math.round(lineRect.getY()) + textMetrics.getAscent();
                g2.drawString(num, x, y);
            }
            g2.dispose();
        }
    }

    public NotebookMe() {
        instanceCount++;
        currentTheme = new InkTheme();
        notebookDir = resolveNotebookDir();
        if (!notebookDir.exists()) notebookDir.mkdirs();
        initLookAndFeel(); initComponents(); initMenuBar(); initStatusBar(); initAutoSave(); initWindowEvents();
        setTitle(APP_NAME + " - untitled");
        setSize(1100, 750); setMinimumSize(new Dimension(700, 500));
        setIconImage(createAppIcon());
        setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setVisible(true);
        getCurrentTextArea().requestFocusInWindow(); showWelcome();
    }

    private File resolveNotebookDir() {
        String explicitDir = System.getProperty("notebookme.dataDir");
        if (explicitDir != null && !explicitDir.trim().isEmpty()) {
            return new File(explicitDir.trim());
        }

        if ("true".equalsIgnoreCase(System.getProperty("notebookme.portable"))) {
            return new File(resolveApplicationDir(), "data");
        }

        return new File(System.getProperty("user.home"), ".notebookme");
    }

    private File resolveApplicationDir() {
        try {
            File source = new File(NotebookMe.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return source.isFile() ? source.getParentFile() : source;
        } catch (Exception ignored) {
            return new File(".").getAbsoluteFile();
        }
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        ModernUI.apply(currentTheme);
    }

    /** Load the Notebook.Me logo icon from resources or file */
    private java.awt.Image createAppIcon() {
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/logo.png");
            if (is != null) {
                return javax.imageio.ImageIO.read(is);
            }
            java.io.File file = new java.io.File("logo.png");
            if (file.exists()) {
                return javax.imageio.ImageIO.read(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback to a simple placeholder if logo.png is not found
        int s = 128;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(50, 50, 50));
        g.fillRoundRect(12, 8, 104, 112, 18, 18);
        g.setColor(new Color(195, 170, 120));
        g.setFont(new Font("Georgia", Font.BOLD, 22));
        g.drawString("N.Me", 32, 54);
        g.dispose();
        return img;
    }

    private void initComponents() {
        rootPanel = new GradientPanel(
            new BorderLayout(0, 8),
            currentTheme.getBackground(),
            currentTheme.getBackground(),
            null,
            0);
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(rootPanel);

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(currentTheme.getMenuBg());
        tabbedPane.setForeground(currentTheme.getForeground());
        tabbedPane.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setUI(new ModernTabbedPaneUI(currentTheme));
        tabbedPane.setOpaque(false);
        tabbedPane.addChangeListener(e -> onTabChanged());
        addNewTab("untitled", null);

        rootNode = new DefaultMutableTreeNode("Notebook Library");
        treeModel = new DefaultTreeModel(rootNode);
        folderTree = new JTree(treeModel);
        folderTree.putClientProperty("JTree.lineStyle", "None");
        folderTree.setBackground(ModernUI.panelColor(currentTheme));
        folderTree.setForeground(currentTheme.getForeground());
        folderTree.setFont(ModernUI.uiFont(Font.PLAIN, 13f));
        folderTree.setRowHeight(30);
        folderTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        folderTree.setCellRenderer(new ModernTreeRenderer());
        folderTree.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount()==2) openFromTree(); } });
        loadFolderTree();

        rebuildWorkspaceLayout();
    }

    private JButton smallBtn(String text) {
        JButton button = new JButton(text);
        button.setFont(ModernUI.uiFont(Font.BOLD, 11f));
        ModernUI.styleButton(button, currentTheme, "secondary");
        return button;
    }

    private JPanel buildSidebarPanel() {
        SurfacePanel sidebar = new SurfacePanel(
            new BorderLayout(0, 10),
            ModernUI.panelColor(currentTheme),
            ModernUI.hairline(currentTheme),
            0);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, ModernUI.hairline(currentTheme)),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));

        JPanel header = ModernUI.transparentPanel(new BorderLayout(0, 10));

        JPanel titleBlock = ModernUI.transparentPanel(new BorderLayout(0, 6));
        JLabel eyebrow = new JLabel("WORKSPACE");
        eyebrow.setFont(ModernUI.uiFont(Font.BOLD, 11f));
        eyebrow.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 135));
        JLabel sideHeader = new JLabel("Library");
        sideHeader.setFont(ModernUI.uiFont(Font.BOLD, 17f));
        sideHeader.setForeground(currentTheme.getForeground());
        titleBlock.add(eyebrow, BorderLayout.NORTH);
        titleBlock.add(sideHeader, BorderLayout.CENTER);

        JPanel sideButtons = new JPanel(new GridLayout(3, 1, 0, 6));
        sideButtons.setOpaque(false);
        JButton addFolder = smallBtn("New Folder");
        JButton addNote = smallBtn("New Note");
        JButton deleteItem = smallBtn("Delete");
        addFolder.setIcon(createIcon("folder"));
        addNote.setIcon(createIcon("note"));
        deleteItem.setIcon(createIcon("delete"));
        ModernUI.styleButton(deleteItem, currentTheme, "danger");
        addFolder.addActionListener(e -> createFolder());
        addNote.addActionListener(e -> createNoteInFolder());
        deleteItem.addActionListener(e -> deleteFromTree());
        sideButtons.add(addFolder);
        sideButtons.add(addNote);
        sideButtons.add(deleteItem);

        header.add(titleBlock, BorderLayout.CENTER);
        header.add(sideButtons, BorderLayout.SOUTH);

        JScrollPane treeScroll = new JScrollPane(folderTree);
        ModernUI.styleScrollPane(treeScroll, currentTheme, ModernUI.panelColor(currentTheme));

        SurfacePanel treeShell = new SurfacePanel(
            new BorderLayout(),
            ModernUI.mix(currentTheme.getBackground(), currentTheme.getMenuBg(), 0.14f),
            ModernUI.hairline(currentTheme),
            ModernUI.RADIUS);
        treeShell.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        treeShell.add(treeScroll, BorderLayout.CENTER);

        sidebar.add(header, BorderLayout.NORTH);
        sidebar.add(treeShell, BorderLayout.CENTER);
        return sidebar;
    }

    private void rebuildWorkspaceLayout() {
        int dividerLocation = splitPane != null ? splitPane.getDividerLocation() : SIDEBAR_WIDTH;
        JPanel sidebar = buildSidebarPanel();

        JPanel centerPanel = ModernUI.transparentPanel(new BorderLayout(0, 8));
        centerPanel.add(buildTopStrip(), BorderLayout.NORTH);

        SurfacePanel editorShell = new SurfacePanel(
            new BorderLayout(),
            ModernUI.panelColor(currentTheme),
            ModernUI.hairline(currentTheme),
            ModernUI.RADIUS);
        editorShell.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tabbedPane.setUI(new ModernTabbedPaneUI(currentTheme));
        tabbedPane.setOpaque(false);
        editorShell.add(tabbedPane, BorderLayout.CENTER);
        centerPanel.add(editorShell, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, centerPanel);
        ModernUI.styleSplitPane(splitPane, currentTheme);
        splitPane.setResizeWeight(0.0);

        if (sidebarVisible) {
            splitPane.getLeftComponent().setVisible(true);
            splitPane.setDividerSize(1);
            splitPane.setDividerLocation(dividerLocation > 0 ? dividerLocation : SIDEBAR_WIDTH);
        } else {
            splitPane.getLeftComponent().setVisible(false);
            splitPane.setDividerSize(0);
            splitPane.setDividerLocation(0);
        }

        if (rootPanel != null) {
            rootPanel.removeAll();
            rootPanel.add(splitPane, BorderLayout.CENTER);
            if (statusBar != null) rootPanel.add(statusBar, BorderLayout.SOUTH);
            rootPanel.revalidate();
            rootPanel.repaint();
        }
    }

    private JPanel buildTopStrip() {
        SurfacePanel strip = new SurfacePanel(
            new BorderLayout(8, 0),
            ModernUI.panelColor(currentTheme),
            ModernUI.hairline(currentTheme),
            ModernUI.RADIUS);
        strip.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        strip.setPreferredSize(new Dimension(0, 52));

        JPanel leftStrip = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sideToggleBtn = new JButton(sidebarVisible ? "<" : ">");
        sideToggleBtn.setPreferredSize(new Dimension(34, 34));
        sideToggleBtn.setFont(ModernUI.uiFont(Font.BOLD, 14f));
        ModernUI.styleButton(sideToggleBtn, currentTheme, "secondary");
        sideToggleBtn.setContentAreaFilled(true);
        sideToggleBtn.setOpaque(true);
        sideToggleBtn.setToolTipText("Toggle sidebar");
        sideToggleBtn.addActionListener(e -> animateSidebar());
        leftStrip.add(sideToggleBtn);

        JLabel brand = new JLabel("notebook.me");
        brand.setFont(ModernUI.uiFont(Font.BOLD, 15f));
        brand.setForeground(currentTheme.getForeground());
        leftStrip.add(brand);
        strip.add(leftStrip, BorderLayout.WEST);

        searchField = new JTextField();
        ModernUI.styleTextField(searchField, currentTheme, false);
        searchField.setMinimumSize(new Dimension(80, 34));
        searchField.setPreferredSize(new Dimension(220, 34));
        searchField.setText("Search notes");
        searchField.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 140));
        searchField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (searchField.getText().equals("Search notes")) {
                    searchField.setText("");
                    searchField.setForeground(currentTheme.getForeground());
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText("Search notes");
                    searchField.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 140));
                }
            }
        });
        searchField.setToolTipText("Quick Search (Enter)");
        searchField.addActionListener(e -> quickSearch());
        strip.add(searchField, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionRow.setOpaque(false);
        JButton newBtn = new JButton("New");
        JButton openBtn = new JButton("Open");
        JButton saveBtn = new JButton("Save");
        Dimension buttonSize = new Dimension(60, 34);
        newBtn.setPreferredSize(buttonSize);
        openBtn.setPreferredSize(buttonSize);
        saveBtn.setPreferredSize(buttonSize);
        ModernUI.styleButton(newBtn, currentTheme, "primary");
        ModernUI.styleButton(openBtn, currentTheme, "secondary");
        ModernUI.styleButton(saveBtn, currentTheme, "secondary");
        newBtn.addActionListener(e -> addNewTab("untitled", null));
        openBtn.addActionListener(e -> openFile());
        saveBtn.addActionListener(e -> { TabData td = currentTab(); if (td != null) saveTabFile(td); });

        actionRow.add(newBtn);
        actionRow.add(openBtn);
        actionRow.add(saveBtn);

        strip.add(actionRow, BorderLayout.EAST);
        return strip;
    }

    private TabData addNewTab(String title, File file) {
        TabData td = new TabData(); td.file = file;
        td.textArea = new EditorTextArea(); td.textArea.setLineWrap(wordWrap); td.textArea.setWrapStyleWord(true); td.textArea.setTabSize(4);
        td.textArea.setBackground(ModernUI.editorColor(currentTheme));
        td.textArea.setForeground(fontColor != null ? fontColor : currentTheme.getForeground());
        td.textArea.setCaretColor(currentTheme.getAccent()); td.textArea.setSelectionColor(ModernUI.withAlpha(currentTheme.getAccent(), 120));
        td.textArea.setSelectedTextColor(currentTheme.getForeground());
        td.textArea.setFont(new Font(fontFamily, Font.PLAIN, fontSize));
        td.textArea.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 22));
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
        td.textArea.addCaretListener(e -> td.linePanel.repaint());
        td.editorPanel = ModernUI.transparentPanel(new BorderLayout());
        td.scrollPane = new JScrollPane(td.textArea);
        ModernUI.styleScrollPane(td.scrollPane, currentTheme, ModernUI.editorColor(currentTheme));
        td.scrollPane.getViewport().addChangeListener(e -> td.linePanel.repaint());
        td.editorPanel.add(td.scrollPane, BorderLayout.CENTER);
        applyLineNumberVisibility(td);
        tabs.add(td); tabbedPane.addTab(title, td.editorPanel);
        int idx = tabbedPane.getTabCount() - 1;
        td.tabHeader = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        td.tabLabel = new JLabel(title);
        td.tabLabel.setForeground(currentTheme.getForeground());
        td.tabLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        td.closeButton = new JButton("x");
        td.closeButton.setFont(ModernUI.uiFont(Font.PLAIN, 14f));
        td.closeButton.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 120));
        td.closeButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        td.closeButton.setContentAreaFilled(false);
        td.closeButton.setFocusPainted(false);
        td.closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        td.closeButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { td.closeButton.setForeground(new Color(220, 84, 84)); }
            public void mouseExited(MouseEvent e) { td.closeButton.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 120)); }
        });
        td.closeButton.addActionListener(e -> closeTab(tabs.indexOf(td)));
        td.tabHeader.add(td.tabLabel);
        td.tabHeader.add(td.closeButton);
        tabbedPane.setTabComponentAt(idx, td.tabHeader);
        tabbedPane.setSelectedIndex(idx);
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

    private void applyLineNumberVisibility(TabData td) {
        if (td == null || td.scrollPane == null || td.linePanel == null) return;
        td.scrollPane.setRowHeaderView(showLineNumbers ? td.linePanel : null);
        if (td.scrollPane.getRowHeader() != null) {
            td.scrollPane.getRowHeader().setOpaque(false);
        }
        td.linePanel.setVisible(showLineNumbers);
        if (showLineNumbers) td.linePanel.refreshMetrics();
        td.scrollPane.revalidate();
        td.scrollPane.repaint();
    }

    private Icon createIcon(String type) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(currentTheme.getForeground());
                int s = 14;
                if (type.equals("folder")) {
                    g2.fillRoundRect(x, y+2, s, s-4, 2, 2);
                    g2.fillRect(x, y+2, s/2, 3);
                } else if (type.equals("note")) {
                    g2.fillRoundRect(x+2, y, s-4, s, 2, 2);
                    g2.setColor(currentTheme.getBackground());
                    g2.fillRect(x+4, y+4, s-8, 2);
                    g2.fillRect(x+4, y+8, s-8, 2);
                } else if (type.equals("delete")) {
                    g2.setStroke(new BasicStroke(2));
                    g2.drawLine(x+2, y+2, x+s-2, y+s-2);
                    g2.drawLine(x+s-2, y+2, x+2, y+s-2);
                }
                g2.dispose();
            }
            @Override public int getIconWidth() { return 14; }
            @Override public int getIconHeight() { return 14; }
        };
    }

    private void initMenuBar() {
        menuBar = new JMenuBar();
        menuBar.setBackground(currentTheme.getMenuBg());
        menuBar.setOpaque(true);
        menuBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ModernUI.hairline(currentTheme)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
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
        ln.addActionListener(e->{showLineNumbers=ln.isSelected();for(TabData td:tabs)applyLineNumberVisibility(td);});
        sb.addActionListener(e->{
            sidebarVisible=sb.isSelected();
            splitPane.getLeftComponent().setVisible(sidebarVisible);
            splitPane.setDividerSize(sidebarVisible ? 1 : 0);
            if (sideToggleBtn != null) sideToggleBtn.setText(sidebarVisible ? "<" : ">");
            if (sidebarVisible && splitPane.getDividerLocation() == 0) splitPane.setDividerLocation(SIDEBAR_WIDTH);
        });
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
        m.add(as);m.addSeparator();m.add(p);m.add(v);m.addSeparator();m.add(tt);m.add(diary);m.addSeparator();m.add(sd); return m;
    }

    private JMenu buildThemeMenu() {
        JMenu m=styledMenu("Theme"); ButtonGroup g=new ButtonGroup();
        String[][] ts={{"Ink (dark)","ink","InkTheme"},{"Parchment (light)","par","ParchmentTheme"},{"Mocha (warm)","moc","MochaTheme"},{"Ocean (blue)","oce","OceanTheme"},{"Sunset (warm)","sun","SunsetTheme"},{"Forest (green)","for","ForestTheme"},
            {"Lavender (soft)","lav","LavenderTheme"},{"Dracula (classic)","dra","DraculaTheme"},{"Nord (arctic)","nor","NordTheme"},{"Solarized (dark)","sol","SolarizedTheme"},{"CRT Terminal","crt","CRTTheme"}};
        for(String[] t:ts){ 
            boolean isSelected = currentTheme.getClass().getSimpleName().equals(t[2]);
            JRadioButtonMenuItem i=new JRadioButtonMenuItem(t[0],isSelected); sri(i); g.add(i);
            final String k=t[1]; i.addActionListener(e->{
                switch(k){case"ink":applyTheme(new InkTheme());break;case"par":applyTheme(new ParchmentTheme());break;
                case"moc":applyTheme(new MochaTheme());break;case"oce":applyTheme(new OceanTheme());break;
                case"sun":applyTheme(new SunsetTheme());break;case"for":applyTheme(new ForestTheme());break;
                case"lav":applyTheme(new LavenderTheme());break;case"dra":applyTheme(new DraculaTheme());break;
                case"nor":applyTheme(new NordTheme());break;case"sol":applyTheme(new SolarizedTheme());break;
                case"crt":applyTheme(new CRTTheme());break;}
            });
            m.add(i); } return m;
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
        rebuildStatusBar();
    }

    private JPanel statusChip(JLabel label) {
        SurfacePanel chip = new SurfacePanel(
            new FlowLayout(FlowLayout.LEFT, 0, 0),
            ModernUI.mix(currentTheme.getMenuBg(), currentTheme.getSecondary(), 0.25f),
            ModernUI.hairline(currentTheme),
            18);
        chip.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        label.setFont(ModernUI.uiFont(Font.PLAIN, 11f));
        label.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 180));
        chip.add(label);
        return chip;
    }

    private void rebuildStatusBar() {
        statusLeft = new JLabel("Ln 1, Col 1");
        statusMid = new JLabel("0 words");
        statusRight = new JLabel("v" + VERSION);
        lastEditedLabel = new JLabel("Not edited yet");

        statusBar = new SurfacePanel(
            new BorderLayout(12, 0),
            currentTheme.getStatusBg(),
            ModernUI.hairline(currentTheme),
            0);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ModernUI.hairline(currentTheme)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel leftPanel = ModernUI.transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JPanel centerPanel = ModernUI.transparentPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        JPanel rightPanel = ModernUI.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JLabel[] labels = { statusLeft, statusMid, lastEditedLabel, statusRight };
        for (JLabel label : labels) {
            label.setFont(ModernUI.uiFont(Font.PLAIN, 11f));
            label.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 175));
        }

        leftPanel.add(statusLeft);
        centerPanel.add(statusMid);
        rightPanel.add(lastEditedLabel);
        rightPanel.add(statusRight);

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(centerPanel, BorderLayout.CENTER);
        statusBar.add(rightPanel, BorderLayout.EAST);

        if (rootPanel != null) {
            rootPanel.add(statusBar, BorderLayout.SOUTH);
            rootPanel.revalidate();
            rootPanel.repaint();
        }
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
        SwingUtilities.updateComponentTreeUI(ch);
        ch.setPreferredSize(new Dimension(800, 600));
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
            setTitle(APP_NAME+" - "+file.getName()); flashStatus("Saved");
        }catch(IOException e){showError("Failed to save:\n"+e.getMessage());}
    }

    private void updateTabTitle(int i, String t) {
        if (i < 0 || i >= tabs.size()) return;
        TabData td = tabs.get(i);
        if (td.tabLabel != null) td.tabLabel.setText(t);
    }

    private void pickFontFamily() {
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search fonts...");
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String f : fonts) model.addElement(f);
        JList<String> list = new JList<>(model);
        list.setSelectedValue(fontFamily, true);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void filter() {
                String q = searchField.getText().toLowerCase();
                model.clear();
                for (String f : fonts) if (f.toLowerCase().contains(q)) model.addElement(f);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        panel.add(searchField, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(250, 200));
        panel.add(sp, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(this, panel, "Choose Font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            fontFamily = list.getSelectedValue();
            applyFontToAll();
        }
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

    private void applyFontToAll() { for(TabData td:tabs){td.textArea.setFont(new Font(fontFamily,Font.PLAIN,fontSize));td.linePanel.repaint();} statusRight.setText(fontSize+"pt - v"+VERSION); }

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
            else{long s=rem/1000;statusRight.setText(String.format("DESTRUCT %d:%02d",s/60,s%60));}});
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
        String q=searchField.getText(); if(q.isEmpty()||q.equals("Search notes"))return; JTextArea ta=getCurrentTextArea();
        String text=ta.getText().toLowerCase(); int from=ta.getCaretPosition(); int idx=text.indexOf(q.toLowerCase(),from);
        if(idx==-1)idx=text.indexOf(q.toLowerCase(),0);
        if(idx>=0){ta.setCaretPosition(idx);ta.select(idx,idx+q.length());ta.requestFocus();flashStatus("Found");}else flashStatus("Not found");
    }

    private void onTextChange(TabData td) {
        if (td.linePanel != null) td.linePanel.refreshMetrics();
        td.modified=true; int i=tabs.indexOf(td); if(i>=0){String n=td.file!=null?td.file.getName():"untitled"; updateTabTitle(i,"* "+n);}
        updateStatus();
        // Update last-edited timestamp with clock format
        String fmt=use24HourClock?"HH:mm:ss":"hh:mm:ss a";
        SwingUtilities.invokeLater(()->lastEditedLabel.setText("Last edited: "+new SimpleDateFormat(fmt).format(new java.util.Date())));
    }

    private void updateStatus() { SwingUtilities.invokeLater(()->{JTextArea ta=getCurrentTextArea();String t=ta.getText();int ch=t.length(),w=t.isBlank()?0:t.trim().split("\\s+").length,l=ta.getLineCount();statusMid.setText(w+" words - "+ch+" chars - "+l+" lines");}); }

    private void updateCaretStatus() { SwingUtilities.invokeLater(()->{try{JTextArea ta=getCurrentTextArea();int pos=ta.getCaretPosition(),line=ta.getLineOfOffset(pos)+1,col=pos-ta.getLineStartOffset(line-1)+1;statusLeft.setText("Ln "+line+", Col "+col);}catch(BadLocationException ignored){}}); }

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
        if (currentTheme instanceof CRTTheme && !isFullScreen) {
            SwingUtilities.invokeLater(this::toggleFullScreen);
        }
    }

    /** Smooth slide animation for sidebar toggle */
    private void animateSidebar() {
        final int targetWidth = SIDEBAR_WIDTH;
        final int delay = 12; // ms per frame
        if (sidebarVisible) {
            // Slide closed
            javax.swing.Timer timer = new javax.swing.Timer(delay, null);
            timer.addActionListener(e -> {
                int current = splitPane.getDividerLocation();
                int step = Math.max(2, current / 6); // Ease out
                if (current > 0) {
                    splitPane.setDividerLocation(Math.max(0, current - step));
                } else {
                    splitPane.setDividerLocation(0);
                    splitPane.getLeftComponent().setVisible(false);
                    splitPane.setDividerSize(0);
                    sidebarVisible = false;
                    sideToggleBtn.setText(">");
                    timer.stop();
                }
            });
            timer.start();
        } else {
            // Slide open
            splitPane.getLeftComponent().setVisible(true);
            splitPane.setDividerSize(1);
            if (splitPane.getDividerLocation() <= 0) {
                splitPane.setDividerLocation(1);
            }
            javax.swing.Timer timer = new javax.swing.Timer(delay, null);
            timer.addActionListener(e -> {
                int current = splitPane.getDividerLocation();
                int remaining = targetWidth - current;
                int step = Math.max(2, remaining / 6); // Ease out
                if (current < targetWidth) {
                    splitPane.setDividerLocation(Math.min(targetWidth, current + step));
                } else {
                    splitPane.setDividerLocation(targetWidth);
                    sidebarVisible = true;
                    sideToggleBtn.setText("<");
                    timer.stop();
                }
            });
            timer.start();
        }
    }

    /** Toggle fullscreen mode - smooth via maximized state */
    private void toggleFullScreen() {
        if (!isFullScreen) {
            dispose();
            setUndecorated(true);
            setVisible(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            isFullScreen = true;
            flashStatus("Fullscreen - press ESC or F11 to exit");
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
        ModernUI.apply(currentTheme);
        Color fg = currentTheme.getForeground();
        Color ac = currentTheme.getAccent();

        if (rootPanel != null) {
            rootPanel.setColors(
                currentTheme.getBackground(),
                currentTheme.getBackground(),
                null);
        }

        folderTree.setBackground(ModernUI.panelColor(currentTheme));
        folderTree.setForeground(fg);

        for (TabData td : tabs) {
            td.textArea.setBackground(ModernUI.editorColor(currentTheme));
            td.textArea.setForeground(fontColor != null ? fontColor : fg);
            td.textArea.setCaretColor(ac);
            td.textArea.setSelectionColor(ModernUI.withAlpha(ac, 120));
            td.textArea.setSelectedTextColor(fg);
            td.textArea.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 22));
            td.linePanel.setTheme(currentTheme);
            if (td.scrollPane != null) ModernUI.styleScrollPane(td.scrollPane, currentTheme, ModernUI.editorColor(currentTheme));
            if (td.tabLabel != null) td.tabLabel.setForeground(fg);
            if (td.closeButton != null) td.closeButton.setForeground(ModernUI.withAlpha(fg, 120));
        }

        initMenuBar();
        rebuildStatusBar();
        rebuildWorkspaceLayout();
        if (dyslexiaMode.enabled) applyDyslexiaMode();
        updateStatus();
        updateCaretStatus();
        repaint();
    }

    private void confirmExit() { for(TabData td:tabs){if(td.modified){int r=JOptionPane.showConfirmDialog(this,"Unsaved changes. Exit?","Exit",JOptionPane.YES_NO_OPTION);if(r!=JOptionPane.YES_OPTION)return;break;}} running=false;autoSaveThread.interrupt();dispose();System.exit(0); }
    private void openFindReplace() { new FindReplaceDialog(this,getCurrentTextArea(),currentTheme).setVisible(true); }
    private void flashStatus(String msg) { SwingUtilities.invokeLater(()->statusRight.setText(msg)); Thread t=new Thread(()->{try{Thread.sleep(2500);}catch(InterruptedException ignored){}SwingUtilities.invokeLater(()->statusRight.setText("v"+VERSION));}); t.setDaemon(true);t.start(); }
    private void showError(String msg) { JOptionPane.showMessageDialog(this,msg,"Error",JOptionPane.ERROR_MESSAGE); }

    // New v3.0 methods
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

    // v4.0 methods
    private void selectNextOccurrence() {
        JTextArea ta=getCurrentTextArea(); String sel=ta.getSelectedText();
        if(sel==null||sel.isEmpty()){
            ta.getHighlighter().removeAllHighlights();
            flashStatus("Highlights cleared");
            return;
        }
        String text=ta.getText();
        Highlighter hl=ta.getHighlighter();
        hl.removeAllHighlights();
        int count=0; int idx=0;
        Color accentAlpha=new Color(currentTheme.getAccent().getRed(),currentTheme.getAccent().getGreen(),currentTheme.getAccent().getBlue(),80);
        while((idx=text.indexOf(sel,idx))>=0){
            try{hl.addHighlight(idx,idx+sel.length(),new DefaultHighlighter.DefaultHighlightPainter(accentAlpha));}catch(BadLocationException ignored){}
            count++; idx+=sel.length();
        }
        if(count>0) {
            flashStatus(count+" occurrences highlighted");
            String rep = JOptionPane.showInputDialog(this, "Edit all " + count + " occurrences of '" + sel + "':", sel);
            if (rep != null) {
                String newTxt = text.replace(sel, rep);
                ta.setText(newTxt);
                flashStatus("Replaced " + count + " occurrences");
            }
            ta.getHighlighter().removeAllHighlights();
        } else {
            flashStatus("No occurrences found");
        }
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
                td.textArea.setBackground(ModernUI.editorColor(currentTheme));
                td.textArea.setForeground(fontColor!=null?fontColor:currentTheme.getForeground());
                td.textArea.setCaretColor(currentTheme.getAccent());
                td.textArea.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 22));
            }
        }
    }

    // v5.5 methods

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
            statusLeft.setText("LOCKED");
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

    // resetDiary moved to DiaryDialog

    private void showWelcome() {
        getCurrentTextArea().setText(
            "Welcome to notebook.me v" + VERSION + " by Vervain Labs\n" +
            "------------------------------------------\n\n" +
            "Start typing, open a file, or create a note from the Library.\n\n" +
            "Quick keys:\n" +
            "  Ctrl+N       New tab\n" +
            "  Ctrl+O       Open file\n" +
            "  Ctrl+S       Save\n" +
            "  Ctrl+F       Find and replace\n" +
            "  Ctrl+M       Markdown preview\n" +
            "  F11          Fullscreen\n");
        getCurrentTextArea().setCaretPosition(0); TabData td=currentTab(); if(td!=null){td.modified=false;td.lastSaved=getCurrentTextArea().getText();}
    }

    private void showShortcuts() {
        JDialog shortcutsDlg = new JDialog(this, "Keyboard Shortcuts", true);
        shortcutsDlg.setSize(540, 610);
        shortcutsDlg.setMinimumSize(new Dimension(460, 500));
        shortcutsDlg.setLocationRelativeTo(this);

        GradientPanel root = new GradientPanel(
            new BorderLayout(0, 12),
            currentTheme.getBackground(),
            currentTheme.getBackground(),
            null,
            0);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel header = ModernUI.transparentPanel(new BorderLayout(12, 0));
        JLabel icon = new JLabel(createInfoIcon(42, currentTheme));
        header.add(icon, BorderLayout.WEST);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Keyboard Shortcuts");
        title.setFont(ModernUI.uiFont(Font.BOLD, 20f));
        title.setForeground(currentTheme.getForeground());
        JLabel subtitle = new JLabel("Fast actions for everyday note editing");
        subtitle.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        subtitle.setForeground(ModernUI.withAlpha(currentTheme.getForeground(), 150));
        titleStack.add(title);
        titleStack.add(Box.createVerticalStrut(3));
        titleStack.add(subtitle);
        header.add(titleStack, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        SurfacePanel listPanel = new SurfacePanel(
            new BorderLayout(),
            ModernUI.panelColor(currentTheme),
            ModernUI.hairline(currentTheme),
            ModernUI.RADIUS);
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[][] shortcuts = {
            {"Ctrl+N", "New tab"},
            {"Ctrl+O", "Open file"},
            {"Ctrl+S", "Save"},
            {"Ctrl+Shift+S", "Save as"},
            {"Ctrl+W", "Close tab"},
            {"Ctrl+Z", "Undo"},
            {"Ctrl+Y", "Redo"},
            {"Ctrl+F", "Find and replace"},
            {"Ctrl+D", "Highlight all"},
            {"Ctrl+M", "Markdown preview"},
            {"Ctrl++ / Ctrl+-", "Zoom"},
            {"F11 / Esc", "Fullscreen"}
        };

        for (String[] shortcut : shortcuts) {
            rows.add(shortcutRow(shortcut[0], shortcut[1]));
            rows.add(Box.createVerticalStrut(4));
        }

        JScrollPane shortcutScroll = new JScrollPane(rows);
        ModernUI.styleScrollPane(shortcutScroll, currentTheme, ModernUI.panelColor(currentTheme));
        shortcutScroll.setBorder(BorderFactory.createEmptyBorder());
        listPanel.add(shortcutScroll, BorderLayout.CENTER);
        root.add(listPanel, BorderLayout.CENTER);

        JPanel footer = ModernUI.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton closeBtn = new JButton("Close");
        ModernUI.styleButton(closeBtn, currentTheme, "primary");
        closeBtn.addActionListener(e -> shortcutsDlg.dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        shortcutsDlg.setContentPane(root);
        shortcutsDlg.getRootPane().setDefaultButton(closeBtn);
        shortcutsDlg.setVisible(true);
    }

    private JPanel shortcutRow(String key, String action) {
        JPanel row = ModernUI.transparentPanel(new BorderLayout(16, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setPreferredSize(new Dimension(0, 36));
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel keyLabel = shortcutKey(key);
        keyLabel.setPreferredSize(new Dimension(124, 30));
        row.add(keyLabel, BorderLayout.WEST);
        row.add(shortcutAction(action), BorderLayout.CENTER);
        return row;
    }

    private JLabel shortcutKey(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(ModernUI.inputColor(currentTheme));
        label.setForeground(ModernUI.mix(currentTheme.getForeground(), currentTheme.getAccent(), 0.18f));
        label.setFont(ModernUI.monoFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(ModernUI.hairline(currentTheme), ModernUI.RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        return label;
    }

    private JLabel shortcutAction(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(currentTheme.getForeground());
        label.setFont(ModernUI.uiFont(Font.PLAIN, 13f));
        return label;
    }

    private Icon createInfoIcon(int size, Theme theme) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = ModernUI.accentSoft(theme);
                Color stroke = ModernUI.mix(theme.getAccent(), theme.getForeground(), 0.20f);
                g2.setColor(fill);
                g2.fillOval(x, y, size - 1, size - 1);
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(x, y, size - 1, size - 1);
                g2.setColor(ModernUI.contrastText(fill));
                g2.setFont(ModernUI.uiFont(Font.BOLD, size * 0.58f));
                FontMetrics fm = g2.getFontMetrics();
                String info = "i";
                int textX = x + (size - fm.stringWidth(info)) / 2;
                int textY = y + (size - fm.getHeight()) / 2 + fm.getAscent() - 1;
                g2.drawString(info, textX, textY);
                g2.dispose();
            }
        };
    }

    private void showAbout() {
        JDialog aboutDlg = new JDialog(this, "About", true);
        aboutDlg.setSize(560, 600);
        aboutDlg.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(currentTheme.getBackground());
        
        String hex = String.format("#%02x%02x%02x", currentTheme.getForeground().getRed(), currentTheme.getForeground().getGreen(), currentTheme.getForeground().getBlue());
        String aboutText = "<html><body><h2 style='color:" + hex + "'>Notebook.Me</h2>" +
                           "<p style='color:" + hex + ";width:350px;font-size:11px;'>" +
                           "A smart and minimal note-taking app designed to help users write, organize, and manage their notes efficiently.<br><br>" +
                           "Built on Java<br>Developed by Vervain Labs.<br>Current version " + VERSION + "<br><br>" +
                           "<b>User trust statement:</b><br>Your privacy matters to us. Your notes remain secure and accessible only to you.<br><br>" +
                           "Instances: " + instanceCount + "</p></body></html>";
        JLabel textLabel = new JLabel(aboutText);
        JLabel githubLabel = new JLabel("<html><a style='color:" + hex + ";text-decoration:none;' href='#'>github.com/VervainLabs</a></html>");
        githubLabel.setFont(ModernUI.uiFont(Font.BOLD, 12f));
        githubLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        githubLabel.setToolTipText("Open Vervain Labs on GitHub");
        githubLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                openExternalLink("https://github.com/VervainLabs");
            }
        });

        JPanel infoStack = new JPanel();
        infoStack.setOpaque(false);
        infoStack.setLayout(new BoxLayout(infoStack, BoxLayout.Y_AXIS));
        infoStack.add(textLabel);
        infoStack.add(Box.createVerticalStrut(4));
        infoStack.add(githubLabel);

        JPanel aboutHeader = new JPanel(new BorderLayout(12, 0));
        aboutHeader.setOpaque(false);
        ImageIcon vervainLogo = loadScaledResourceIcon("vervain-logo.png", 112, 112);
        if (vervainLogo != null) {
            JLabel logoLabel = new JLabel(vervainLogo, SwingConstants.CENTER);
            logoLabel.setPreferredSize(new Dimension(126, 116));
            aboutHeader.add(logoLabel, BorderLayout.WEST);
        }
        aboutHeader.add(infoStack, BorderLayout.CENTER);
        mainPanel.add(aboutHeader, BorderLayout.NORTH);
        
        JPanel tttPanel = new JPanel(new BorderLayout(5, 5));
        tttPanel.setOpaque(false);
        JLabel title = new JLabel("You vs The Notebook", SwingConstants.CENTER);
        title.setFont(ModernUI.uiFont(Font.BOLD, 14f));
        title.setForeground(currentTheme.getForeground());
        tttPanel.add(title, BorderLayout.NORTH);
        
        JPanel grid = new JPanel(new GridLayout(3, 3, 4, 4));
        grid.setOpaque(false);
        JButton[] btns = new JButton[9];
        for (int i=0; i<9; i++) {
            btns[i] = new JButton("");
            btns[i].setFont(ModernUI.uiFont(Font.BOLD, 24f));
            btns[i].setFocusPainted(false);
            btns[i].setBackground(ModernUI.panelColor(currentTheme));
            btns[i].setForeground(currentTheme.getForeground());
            final int idx = i;
            btns[i].addActionListener(e -> {
                if (!btns[idx].getText().isEmpty()) return;
                btns[idx].setText("X");
                if (checkWin(btns, "X")) { JOptionPane.showMessageDialog(aboutDlg, "You win!"); resetTTT(btns); return; }
                if (isFull(btns)) { JOptionPane.showMessageDialog(aboutDlg, "Draw!"); resetTTT(btns); return; }
                
                for(JButton b : btns) b.setEnabled(false);
                
                javax.swing.Timer timer = new javax.swing.Timer(500, ev -> {
                    makeAIMove(btns);
                    for(JButton b : btns) b.setEnabled(true);
                    if (checkWin(btns, "O")) { JOptionPane.showMessageDialog(aboutDlg, "The Notebook wins!"); resetTTT(btns); return; }
                    if (isFull(btns)) { JOptionPane.showMessageDialog(aboutDlg, "Draw!"); resetTTT(btns); return; }
                });
                timer.setRepeats(false);
                timer.start();
            });
            grid.add(btns[i]);
        }
        tttPanel.add(grid, BorderLayout.CENTER);
        
        JButton resetBtn = new JButton("Reset Game");
        ModernUI.styleButton(resetBtn, currentTheme, "secondary");
        resetBtn.addActionListener(e -> resetTTT(btns));
        tttPanel.add(resetBtn, BorderLayout.SOUTH);
        
        mainPanel.add(tttPanel, BorderLayout.CENTER);
        aboutDlg.setContentPane(mainPanel);
        aboutDlg.setVisible(true);
    }

    private void openExternalLink(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ex) {
            showError("Could not open link:\n" + url);
        }
    }

    private ImageIcon loadScaledResourceIcon(String resourceName, int maxWidth, int maxHeight) {
        java.net.URL imageUrl = getClass().getResource("/" + resourceName);
        if (imageUrl == null) return null;

        Image image;
        int width;
        int height;
        try {
            java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(imageUrl);
            if (original == null) return null;

            width = original.getWidth();
            height = original.getHeight();
            int cropSize = Math.min(width, height);
            if (Math.max(width, height) / (double) cropSize > 1.2) {
                cropSize = (int) Math.round(cropSize * 0.62);
            }
            int cropX = Math.max(0, (width - cropSize) / 2);
            int cropY = Math.max(0, (height - cropSize) / 2);
            image = original.getSubimage(cropX, cropY, cropSize, cropSize);
            width = cropSize;
            height = cropSize;
        } catch (IOException ex) {
            ImageIcon original = new ImageIcon(imageUrl);
            width = original.getIconWidth();
            height = original.getIconHeight();
            if (width <= 0 || height <= 0) return null;
            image = original.getImage();
        }

        double scale = Math.min(maxWidth / (double) width, maxHeight / (double) height);
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        Image scaledImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }
    
    private boolean checkWin(JButton[] b, String p) {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] w : wins) if (b[w[0]].getText().equals(p) && b[w[1]].getText().equals(p) && b[w[2]].getText().equals(p)) return true;
        return false;
    }
    private boolean isFull(JButton[] b) {
        for(JButton btn: b) if(btn.getText().isEmpty()) return false;
        return true;
    }
    private void resetTTT(JButton[] b) {
        for(JButton btn: b) btn.setText("");
    }

    private void makeAIMove(JButton[] btns) {
        if (tryMove(btns, "O")) return;
        if (tryMove(btns, "X")) return;
        if (btns[4].getText().isEmpty()) { btns[4].setText("O"); return; }
        java.util.List<Integer> empty = new java.util.ArrayList<>();
        for (int j=0; j<9; j++) if(btns[j].getText().isEmpty()) empty.add(j);
        if (!empty.isEmpty()) btns[empty.get(new java.util.Random().nextInt(empty.size()))].setText("O");
    }

    private boolean tryMove(JButton[] b, String p) {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] w : wins) {
            int count = 0; int empty = -1;
            for (int i : w) {
                if (b[i].getText().equals(p)) count++;
                else if (b[i].getText().isEmpty()) empty = i;
            }
            if (count == 2 && empty != -1) { b[empty].setText("O"); return true; }
        }
        return false;
    }

    private JMenu styledMenu(String text) {
        JMenu menu = new JMenu(text);
        menu.setForeground(ModernUI.mix(currentTheme.getForeground(), currentTheme.getAccent(), 0.12f));
        menu.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        menu.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        menu.getPopupMenu().setBackground(ModernUI.cardColor(currentTheme));
        menu.getPopupMenu().setBorder(new CompoundBorder(
            new RoundedBorder(ModernUI.hairline(currentTheme), ModernUI.RADIUS, 1),
            BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        return menu;
    }

    private JMenuItem si(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setOpaque(true);
        item.setBackground(ModernUI.cardColor(currentTheme));
        item.setForeground(currentTheme.getForeground());
        item.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        item.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        item.setBorderPainted(false);
        return item;
    }

    private void sci(JCheckBoxMenuItem item) {
        item.setOpaque(true);
        item.setBackground(ModernUI.cardColor(currentTheme));
        item.setForeground(currentTheme.getForeground());
        item.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        item.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    }

    private void sri(JRadioButtonMenuItem item) {
        item.setOpaque(true);
        item.setBackground(ModernUI.cardColor(currentTheme));
        item.setForeground(currentTheme.getForeground());
        item.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        item.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    }

    // Modern tree cell renderer
    class ModernTreeRenderer extends DefaultTreeCellRenderer {
        private final Icon libraryIcon = createIcon("folder");
        private final Icon folderClosedIcon = createIcon("folder");
        private final Icon folderOpenIcon = createIcon("folder");
        private final Icon noteIcon = createIcon("note");

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            String text = value.toString();
            label.setFont(ModernUI.uiFont(Font.PLAIN, 13f));
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            int depth = node.getLevel();
            if (depth == 0) {
                label.setText("Library");
                label.setFont(ModernUI.uiFont(Font.BOLD, 13f));
                label.setIcon(libraryIcon);
            } else if (depth == 1) {
                label.setText(text);
                label.setFont(ModernUI.uiFont(Font.BOLD, 12f));
                label.setIcon(expanded ? folderOpenIcon : folderClosedIcon);
            } else {
                label.setText(text);
                label.setIcon(noteIcon);
            }
            if (sel) {
                label.setBackground(ModernUI.accentSoft(currentTheme));
                label.setForeground(currentTheme.getForeground());
                label.setOpaque(true);
            } else {
                label.setBackground(ModernUI.panelColor(currentTheme));
                label.setForeground(depth == 0 ? ModernUI.mix(currentTheme.getForeground(), currentTheme.getAccent(), 0.18f) : currentTheme.getForeground());
                label.setOpaque(true);
            }
            return label;
        }
    }

    class EditorTextArea extends JTextArea {
        private String suggestion = null;

        public EditorTextArea() {
            super();
            addCaretListener(e -> checkMathExpression());
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "tabAction");
            getActionMap().put("tabAction", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (suggestion != null) {
                        insert(suggestion, getCaretPosition());
                        suggestion = null;
                        repaint();
                    } else {
                        replaceSelection("\t");
                    }
                }
            });
        }

        private void checkMathExpression() {
            suggestion = null;
            try {
                int caret = getCaretPosition();
                int start = javax.swing.text.Utilities.getRowStart(this, caret);
                String line = getText(start, caret - start).trim();
                
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|.*?\\s)((?:\\-?\\d+(?:\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/]\\s*(?:\\-?\\d+(?:\\.\\d+)?))+)$").matcher(line);
                if (m.find()) {
                    String expr = m.group(1).trim();
                    Double result = eval(expr);
                    if (result != null) {
                        String resStr = result == Math.floor(result) ? String.valueOf(result.longValue()) : String.valueOf(result);
                        suggestion = " = " + resStr;
                    }
                }
            } catch (Exception ex) {}
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (suggestion != null) {
                try {
                    Rectangle rect = modelToView(getCaretPosition());
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(getFont());
                    g2.setColor(ModernUI.withAlpha(getForeground(), 120));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(suggestion, rect.x, rect.y + fm.getAscent());
                    g2.dispose();
                } catch (Exception ex) {}
            }
        }
    }

    private static Double eval(String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            Double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) return null;
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected: " + (char)ch);
                }
                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(NotebookMe::new); }
}
