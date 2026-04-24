/**
 * notebook.me v2.0 — Feature-rich Java Notepad
 */
import java.awt.*;
import java.awt.event.*;
import java.io.*;
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
    private static final String VERSION  = "2.0";
    private static int instanceCount = 0;
    private Theme currentTheme;
    private boolean wordWrap = true;
    private int fontSize = 15;
    private String fontFamily = "JetBrains Mono";
    private Color fontColor = null;
    private boolean showLineNumbers = true;
    private JTabbedPane tabbedPane;
    private List<TabData> tabs = new ArrayList<>();
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private File notebookDir;
    private JSplitPane splitPane;
    private boolean sidebarVisible = true;
    private JLabel statusLeft, statusMid, statusRight;
    private JPanel statusBar;
    private JMenuBar menuBar;
    private JTextField searchField;
    private Thread autoSaveThread;
    private volatile boolean running = true;

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
        setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setVisible(true);
        getCurrentTextArea().requestFocusInWindow(); showWelcome();
    }

    private void initLookAndFeel() { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {} }

    private void initComponents() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(currentTheme.getBackground());
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(currentTheme.getMenuBg());
        tabbedPane.setForeground(currentTheme.getForeground());
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabbedPane.addChangeListener(e -> onTabChanged());
        addNewTab("untitled", null);
        rootNode = new DefaultMutableTreeNode("Notebooks");
        treeModel = new DefaultTreeModel(rootNode);
        folderTree = new JTree(treeModel);
        folderTree.setBackground(currentTheme.getMenuBg());
        folderTree.setForeground(currentTheme.getForeground());
        folderTree.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        folderTree.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        folderTree.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount()==2) openFromTree(); } });
        loadFolderTree();
        JScrollPane treeScroll = new JScrollPane(folderTree);
        treeScroll.setPreferredSize(new Dimension(200, 0));
        treeScroll.setBorder(BorderFactory.createMatteBorder(0,0,0,1,currentTheme.getBorder()));
        treeScroll.getViewport().setBackground(currentTheme.getMenuBg());
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(currentTheme.getMenuBg());
        JPanel sideButtons = new JPanel(new GridLayout(1, 3, 2, 0));
        sideButtons.setBackground(currentTheme.getMenuBg());
        JButton af = smallBtn("+ Folder"); JButton an = smallBtn("+ Note"); JButton dl = smallBtn("Delete");
        af.addActionListener(e -> createFolder()); an.addActionListener(e -> createNoteInFolder()); dl.addActionListener(e -> deleteFromTree());
        sideButtons.add(af); sideButtons.add(an); sideButtons.add(dl);
        sidePanel.add(sideButtons, BorderLayout.NORTH);
        sidePanel.add(treeScroll, BorderLayout.CENTER);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(currentTheme.getBackground());
        centerPanel.add(buildTopStrip(), BorderLayout.NORTH);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, centerPanel);
        splitPane.setDividerLocation(200); splitPane.setDividerSize(3); splitPane.setBorder(null);
        getContentPane().add(splitPane, BorderLayout.CENTER);
    }

    private JButton smallBtn(String text) {
        JButton b = new JButton(text); b.setFont(new Font("Segoe UI",Font.PLAIN,10));
        b.setBackground(currentTheme.getSecondary()); b.setForeground(currentTheme.getForeground());
        b.setFocusPainted(false); b.setBorderPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }

    private JPanel buildTopStrip() {
        JPanel strip = new JPanel(new BorderLayout());
        strip.setBackground(currentTheme.getBackground());
        strip.setBorder(BorderFactory.createMatteBorder(0,0,1,0,currentTheme.getBorder()));
        strip.setPreferredSize(new Dimension(0, 36));
        JLabel logo = new JLabel("  notebook.me"); logo.setFont(new Font("Georgia",Font.ITALIC,13)); logo.setForeground(currentTheme.getAccent());
        strip.add(logo, BorderLayout.WEST);
        JPanel sp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4)); sp.setBackground(currentTheme.getBackground());
        searchField = new JTextField(18); searchField.setFont(new Font("Consolas",Font.PLAIN,12));
        searchField.setBackground(currentTheme.getSecondary()); searchField.setForeground(currentTheme.getForeground());
        searchField.setCaretColor(currentTheme.getAccent());
        searchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(currentTheme.getBorder()), BorderFactory.createEmptyBorder(2,6,2,6)));
        searchField.setToolTipText("Quick Search (Enter)"); searchField.addActionListener(e -> quickSearch());
        JButton sb = smallBtn("Search"); sb.addActionListener(e -> quickSearch());
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
        menuBar.add(buildThemeMenu()); menuBar.add(buildHelpMenu()); setJMenuBar(menuBar);
    }

    private JMenu buildFileMenu() {
        JMenu m=styledMenu("File");
        JMenuItem n=si("New Tab"); n.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,InputEvent.CTRL_DOWN_MASK));
        JMenuItem o=si("Open..."); o.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_DOWN_MASK));
        JMenuItem s=si("Save"); s.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK));
        JMenuItem sa=si("Save As..."); sa.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
        JMenuItem ct=si("Close Tab"); ct.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,InputEvent.CTRL_DOWN_MASK));
        JMenuItem ex=si("Exit");
        n.addActionListener(e->addNewTab("untitled",null)); o.addActionListener(e->openFile());
        s.addActionListener(e->{TabData td=currentTab();if(td!=null)saveTabFile(td);}); sa.addActionListener(e->saveFileAs());
        ct.addActionListener(e->closeTab(tabbedPane.getSelectedIndex())); ex.addActionListener(e->confirmExit());
        m.add(n);m.add(o);m.addSeparator();m.add(s);m.add(sa);m.addSeparator();m.add(ct);m.addSeparator();m.add(ex); return m;
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
        u.addActionListener(e->{TabData td=currentTab();if(td!=null&&td.undoManager.canUndo())td.undoManager.undo();});
        r.addActionListener(e->{TabData td=currentTab();if(td!=null&&td.undoManager.canRedo())td.undoManager.redo();});
        x.addActionListener(e->getCurrentTextArea().cut()); c.addActionListener(e->getCurrentTextArea().copy());
        v.addActionListener(e->getCurrentTextArea().paste()); a.addActionListener(e->getCurrentTextArea().selectAll());
        f.addActionListener(e->openFindReplace());
        m.add(u);m.add(r);m.addSeparator();m.add(x);m.add(c);m.add(v);m.addSeparator();m.add(a);m.addSeparator();m.add(f); return m;
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
        m.add(zi);m.add(zo);m.add(zr);m.addSeparator();m.add(wr);m.add(ln);m.add(sb); return m;
    }

    private JMenu buildFormatMenu() {
        JMenu m=styledMenu("Format");
        JMenuItem ff=si("Font Family..."); JMenuItem fs=si("Font Size..."); JMenuItem fc=si("Font Color..."); JMenuItem hl=si("Highlight Selection");
        ff.addActionListener(e->pickFontFamily()); fs.addActionListener(e->pickFontSize()); fc.addActionListener(e->pickFontColor()); hl.addActionListener(e->highlightSelection());
        m.add(ff);m.add(fs);m.add(fc);m.addSeparator();m.add(hl); return m;
    }

    private JMenu buildInsertMenu() {
        JMenu m=styledMenu("Insert");
        JMenuItem dr=si("Drawing..."); JMenuItem vd=si("View Drawing at Cursor"); JMenuItem tb=si("Table...");
        dr.addActionListener(e->openDrawingPad()); vd.addActionListener(e->viewDrawingAtCursor()); tb.addActionListener(e->insertTable());
        m.add(dr);m.add(vd);m.addSeparator();m.add(tb); return m;
    }

    private JMenu buildToolsMenu() {
        JMenu m=styledMenu("Tools");
        JMenuItem p=si("Pin/Unpin Note"); JMenuItem v=si("Version History..."); JMenuItem sd=si("Self-Destruct Timer...");
        p.addActionListener(e->togglePin()); v.addActionListener(e->showVersionHistory()); sd.addActionListener(e->setSelfDestruct());
        m.add(p);m.add(v);m.addSeparator();m.add(sd); return m;
    }

    private JMenu buildThemeMenu() {
        JMenu m=styledMenu("Theme"); ButtonGroup g=new ButtonGroup();
        String[][] ts={{"Ink (dark)","ink"},{"Parchment (light)","par"},{"Mocha (warm)","moc"},{"Ocean (blue)","oce"},{"Sunset (warm)","sun"},{"Forest (green)","for"}};
        boolean first=true;
        for(String[] t:ts){ JRadioButtonMenuItem i=new JRadioButtonMenuItem(t[0],first); sri(i); g.add(i);
            final String k=t[1]; i.addActionListener(e->{switch(k){case"ink":applyTheme(new InkTheme());break;case"par":applyTheme(new ParchmentTheme());break;
            case"moc":applyTheme(new MochaTheme());break;case"oce":applyTheme(new OceanTheme());break;case"sun":applyTheme(new SunsetTheme());break;case"for":applyTheme(new ForestTheme());break;}});
            m.add(i); first=false; } return m;
    }

    private JMenu buildHelpMenu() {
        JMenu m=styledMenu("Help"); JMenuItem sc=si("Keyboard Shortcuts"); JMenuItem ab=si("About");
        sc.addActionListener(e->showShortcuts()); ab.addActionListener(e->showAbout());
        m.add(sc);m.addSeparator();m.add(ab); return m;
    }

    private void initStatusBar() {
        statusBar=new JPanel(new BorderLayout()); statusBar.setBackground(currentTheme.getStatusBg());
        statusBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0,currentTheme.getBorder()));
        statusBar.setPreferredSize(new Dimension(0,26));
        Font sf=new Font("Consolas",Font.PLAIN,11);
        Color sfg=new Color(currentTheme.getForeground().getRed(),currentTheme.getForeground().getGreen(),currentTheme.getForeground().getBlue(),160);
        statusLeft=new JLabel("  Ln 1, Col 1"); statusMid=new JLabel("0 words",JLabel.CENTER); statusRight=new JLabel("notebook.me v"+VERSION+"  ");
        for(JLabel l:new JLabel[]{statusLeft,statusMid,statusRight}){l.setFont(sf);l.setForeground(sfg);}
        statusBar.add(statusLeft,BorderLayout.WEST); statusBar.add(statusMid,BorderLayout.CENTER); statusBar.add(statusRight,BorderLayout.EAST);
        getContentPane().add(statusBar,BorderLayout.SOUTH);
    }

    private void initAutoSave() {
        Runnable task=()->{while(running){try{Thread.sleep(30000);synchronized(this){for(TabData td:tabs){if(td.modified&&td.file!=null){saveTabFile(td);flashStatus("Auto-saved");}}}}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}};
        autoSaveThread=new Thread(task,"AutoSave"); autoSaveThread.setDaemon(true); autoSaveThread.setPriority(Thread.MIN_PRIORITY); autoSaveThread.start();
    }

    private void initWindowEvents() { addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){confirmExit();}}); }

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
        JFileChooser ch=new JFileChooser(); ch.setDialogTitle("Save As"); if(td.file!=null)ch.setSelectedFile(td.file);
        if(ch.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){File f=ch.getSelectedFile(); if(!f.getName().contains("."))f=new File(f.getAbsolutePath()+".txt"); writeToFile(td,f);}
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
        String[] opts={"5 minutes","15 minutes","30 minutes","1 hour","Cancel Timer"};
        String picked=(String)JOptionPane.showInputDialog(this,"Self-destruct after:","Self-Destruct",JOptionPane.WARNING_MESSAGE,null,opts,opts[0]);
        if(picked==null)return; if(td.selfDestructTimer!=null)td.selfDestructTimer.stop();
        if(picked.equals("Cancel Timer")){td.selfDestructTime=0;flashStatus("Timer cancelled");return;}
        int mins=picked.startsWith("5")?5:picked.startsWith("15")?15:picked.startsWith("30")?30:60;
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
        String q=searchField.getText(); if(q.isEmpty())return; JTextArea ta=getCurrentTextArea();
        String text=ta.getText().toLowerCase(); int from=ta.getCaretPosition(); int idx=text.indexOf(q.toLowerCase(),from);
        if(idx==-1)idx=text.indexOf(q.toLowerCase(),0);
        if(idx>=0){ta.setCaretPosition(idx);ta.select(idx,idx+q.length());ta.requestFocus();flashStatus("Found");}else flashStatus("Not found");
    }

    private void onTextChange(TabData td) { td.modified=true; int i=tabs.indexOf(td); if(i>=0){String n=td.file!=null?td.file.getName():"untitled"; updateTabTitle(i,"* "+n);} updateStatus(); }

    private void updateStatus() { SwingUtilities.invokeLater(()->{JTextArea ta=getCurrentTextArea();String t=ta.getText();int ch=t.length(),w=t.isBlank()?0:t.trim().split("\\s+").length,l=ta.getLineCount();statusMid.setText(w+" words  ·  "+ch+" chars  ·  "+l+" lines");}); }

    private void updateCaretStatus() { SwingUtilities.invokeLater(()->{try{JTextArea ta=getCurrentTextArea();int pos=ta.getCaretPosition(),line=ta.getLineOfOffset(pos)+1,col=pos-ta.getLineStartOffset(line-1)+1;statusLeft.setText("  Ln "+line+", Col "+col);}catch(BadLocationException ignored){}}); }

    private void changeFontSize(int d) { fontSize=Math.max(9,Math.min(72,fontSize+d)); applyFontToAll(); }

    private void applyTheme(Theme theme) { currentTheme=theme; applyThemeUI(); }

    private void applyThemeUI() {
        Color bg=currentTheme.getBackground(),fg=currentTheme.getForeground(),ac=currentTheme.getAccent();
        for(TabData td:tabs){td.textArea.setBackground(bg);td.textArea.setForeground(fontColor!=null?fontColor:fg);td.textArea.setCaretColor(ac);td.textArea.setSelectionColor(ac.darker());td.textArea.setSelectedTextColor(bg);td.linePanel.setTheme(currentTheme);}
        getContentPane().setBackground(bg); menuBar.setBackground(currentTheme.getMenuBg()); menuBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,currentTheme.getBorder()));
        statusBar.setBackground(currentTheme.getStatusBg()); statusBar.setBorder(BorderFactory.createMatteBorder(1,0,0,0,currentTheme.getBorder()));
        Color sfg=new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),160); statusLeft.setForeground(sfg);statusMid.setForeground(sfg);statusRight.setForeground(sfg);
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
    private void flashStatus(String msg) { SwingUtilities.invokeLater(()->statusRight.setText("  "+msg+"  ")); Thread t=new Thread(()->{try{Thread.sleep(2500);}catch(InterruptedException ignored){}SwingUtilities.invokeLater(()->statusRight.setText("  "+fontSize+"pt  ·  v"+VERSION+"  "));}); t.setDaemon(true);t.start(); }
    private void showError(String msg) { JOptionPane.showMessageDialog(this,msg,"Error",JOptionPane.ERROR_MESSAGE); }

    private void showWelcome() {
        getCurrentTextArea().setText("  Welcome to notebook.me v2.0\n  ───────────────────────────\n\n  NEW FEATURES:\n   Multiple tabs (Ctrl+N/W)\n   6 themes (Ink/Parchment/Mocha/Ocean/Sunset/Forest)\n   Folder system (sidebar)\n   Font family, size & color\n   Line numbers toggle\n   Syntax highlighting\n   Highlight text with colors\n   Drawing pad & Table insert\n   Version history (last 5 saves)\n   Pin important notes\n   Quick search bar\n   Self-destruct timer\n\n  Start typing to begin.\n");
        getCurrentTextArea().setCaretPosition(0); TabData td=currentTab(); if(td!=null){td.modified=false;td.lastSaved=getCurrentTextArea().getText();}
    }

    private void showShortcuts() { JOptionPane.showMessageDialog(this,"Ctrl+N  New tab\nCtrl+O  Open\nCtrl+S  Save\nCtrl+W  Close tab\nCtrl+Shift+S  Save As\nCtrl+Z  Undo\nCtrl+Y  Redo\nCtrl+F  Find & Replace\nCtrl+A  Select All\nCtrl++/- Zoom","Shortcuts",JOptionPane.INFORMATION_MESSAGE); }
    private void showAbout() { JOptionPane.showMessageDialog(this,"notebook.me v"+VERSION+"\nFeature-rich Java Notepad\n\nTabs, Themes, Folders, Drawing,\nTables, Version History, Syntax\nHighlighting, Self-Destruct & more.\n\nInstances: "+instanceCount,"About",JOptionPane.INFORMATION_MESSAGE); }

    private JMenu styledMenu(String t){JMenu m=new JMenu(t);m.setForeground(currentTheme.getForeground());m.setFont(new Font("Segoe UI",Font.PLAIN,12));m.getPopupMenu().setBackground(currentTheme.getMenuBg());m.getPopupMenu().setBorder(BorderFactory.createLineBorder(currentTheme.getBorder()));return m;}
    private JMenuItem si(String t){JMenuItem i=new JMenuItem(t);i.setBackground(currentTheme.getMenuBg());i.setForeground(currentTheme.getForeground());i.setFont(new Font("Segoe UI",Font.PLAIN,12));i.setBorderPainted(false);return i;}
    private void sci(JCheckBoxMenuItem i){i.setBackground(currentTheme.getMenuBg());i.setForeground(currentTheme.getForeground());i.setFont(new Font("Segoe UI",Font.PLAIN,12));}
    private void sri(JRadioButtonMenuItem i){i.setBackground(currentTheme.getMenuBg());i.setForeground(currentTheme.getForeground());i.setFont(new Font("Segoe UI",Font.PLAIN,12));}

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
