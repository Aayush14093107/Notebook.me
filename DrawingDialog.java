import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;

class DrawingDialog extends JDialog {
    private final Theme theme;
    private final File saveDir;
    private BufferedImage canvas;
    private Graphics2D g2d;
    private Color drawColor = Color.WHITE;
    private int brushSize = 3;
    private int prevX = -1;
    private int prevY = -1;
    private File savedFile = null;
    private boolean isEraser = false;
    private JPanel colorPreview;

    public DrawingDialog(JFrame parent, Theme theme, File saveDir) {
        super(parent, "Drawing Pad", true);
        this.theme = theme;
        this.saveDir = saveDir;
        setSize(760, 600);
        setLocationRelativeTo(parent);
        buildUI();
    }

    private void buildUI() {
        if (!saveDir.exists()) saveDir.mkdirs();

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
        JLabel title = new JLabel("Drawing pad");
        title.setFont(ModernUI.uiFont(Font.BOLD, 18f));
        title.setForeground(theme.getForeground());
        titleStack.add(title);
        header.add(titleStack, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        canvas = new BufferedImage(700, 430, BufferedImage.TYPE_INT_ARGB);
        g2d = canvas.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(ModernUI.editorColor(theme));
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        JPanel drawPanel = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                graphics.drawImage(canvas, 0, 0, null);
            }
        };
        drawPanel.setPreferredSize(new Dimension(canvas.getWidth(), canvas.getHeight()));
        drawPanel.setBackground(ModernUI.editorColor(theme));
        drawPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                prevX = e.getX();
                prevY = e.getY();
                drawDot(e.getX(), e.getY());
                drawPanel.repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                prevX = -1;
                prevY = -1;
            }
        });
        drawPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (prevX == -1) return;
                g2d.setColor(isEraser ? ModernUI.editorColor(theme) : drawColor);
                g2d.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.drawLine(prevX, prevY, e.getX(), e.getY());
                prevX = e.getX();
                prevY = e.getY();
                drawPanel.repaint();
            }
        });

        JScrollPane canvasScroll = new JScrollPane(drawPanel);
        ModernUI.styleScrollPane(canvasScroll, theme, ModernUI.editorColor(theme));
        SurfacePanel canvasShell = new SurfacePanel(
            new BorderLayout(),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        canvasShell.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        canvasShell.add(canvasScroll, BorderLayout.CENTER);
        root.add(canvasShell, BorderLayout.CENTER);

        SurfacePanel toolbar = new SurfacePanel(
            new FlowLayout(FlowLayout.LEFT, 8, 8),
            ModernUI.panelColor(theme),
            ModernUI.hairline(theme),
            ModernUI.RADIUS);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton colorBtn = button("Color", "secondary");
        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(20, 20));
        colorPreview.setBackground(drawColor);
        colorPreview.setBorder(new RoundedBorder(ModernUI.hairline(theme), ModernUI.RADIUS, 1));
        colorBtn.addActionListener(e -> {
            Color picked = JColorChooser.showDialog(this, "Pick draw color", drawColor);
            if (picked != null) {
                drawColor = picked;
                colorPreview.setBackground(picked);
                isEraser = false;
            }
        });

        JLabel sizeLabel = new JLabel("Brush");
        sizeLabel.setForeground(theme.getForeground());
        sizeLabel.setFont(ModernUI.uiFont(Font.PLAIN, 12f));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 40, 1));
        sizeSpinner.setPreferredSize(new Dimension(64, 36));
        ModernUI.styleSpinner(sizeSpinner, theme);
        sizeSpinner.addChangeListener(e -> brushSize = (int) sizeSpinner.getValue());

        JButton eraserBtn = button("Eraser", "secondary");
        eraserBtn.addActionListener(e -> {
            isEraser = !isEraser;
            eraserBtn.setText(isEraser ? "Eraser On" : "Eraser");
        });

        JButton clearBtn = button("Clear", "ghost");
        clearBtn.addActionListener(e -> {
            g2d.setColor(ModernUI.editorColor(theme));
            g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            drawPanel.repaint();
        });

        JButton saveBtn = button("Save & Insert", "primary");
        saveBtn.addActionListener(e -> saveDrawing());

        JButton cancelBtn = button("Cancel", "ghost");
        cancelBtn.addActionListener(e -> dispose());

        toolbar.add(colorBtn);
        toolbar.add(colorPreview);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(sizeLabel);
        toolbar.add(sizeSpinner);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(eraserBtn);
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(saveBtn);
        toolbar.add(cancelBtn);
        root.add(toolbar, BorderLayout.SOUTH);
    }

    private JButton button(String text, String variant) {
        JButton button = new JButton(text);
        ModernUI.styleButton(button, theme, variant);
        return button;
    }

    private void drawDot(int x, int y) {
        g2d.setColor(isEraser ? ModernUI.editorColor(theme) : drawColor);
        g2d.fillOval(x - brushSize / 2, y - brushSize / 2, brushSize, brushSize);
    }

    private void saveDrawing() {
        String filename = "drawing_" + System.currentTimeMillis() + ".png";
        File outFile = new File(saveDir, filename);
        try {
            ImageIO.write(canvas, "png", outFile);
            savedFile = outFile;
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save drawing:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public File getSavedFile() { return savedFile; }
}
