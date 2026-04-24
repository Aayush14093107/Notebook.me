import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;

// ════════════════════════════════════════════════════════
//  Drawing Canvas Dialog — saves as PNG
// ════════════════════════════════════════════════════════
class DrawingDialog extends JDialog {
    private BufferedImage canvas;
    private Graphics2D g2d;
    private Color drawColor = Color.WHITE;
    private int brushSize = 3;
    private int prevX = -1, prevY = -1;
    private File savedFile = null;
    private boolean isEraser = false;

    public DrawingDialog(JFrame parent, Theme theme, File saveDir) {
        super(parent, "Drawing Pad", true);
        setSize(700, 550);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Ensure drawings directory exists
        if (!saveDir.exists()) saveDir.mkdirs();

        canvas = new BufferedImage(680, 420, BufferedImage.TYPE_INT_ARGB);
        g2d = canvas.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(30, 30, 35));
        g2d.fillRect(0, 0, 680, 420);

        JPanel drawPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(canvas, 0, 0, null);
            }
        };
        drawPanel.setPreferredSize(new Dimension(680, 420));
        drawPanel.setBackground(new Color(30, 30, 35));
        drawPanel.setBorder(BorderFactory.createLineBorder(theme.getBorder(), 1));

        drawPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { prevX = e.getX(); prevY = e.getY(); drawDot(e.getX(), e.getY()); drawPanel.repaint(); }
            public void mouseReleased(MouseEvent e) { prevX = -1; prevY = -1; }
        });
        drawPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (prevX != -1) {
                    g2d.setColor(isEraser ? new Color(30, 30, 35) : drawColor);
                    g2d.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawLine(prevX, prevY, e.getX(), e.getY());
                    prevX = e.getX(); prevY = e.getY();
                    drawPanel.repaint();
                }
            }
        });

        // ── Toolbar ──
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.setBackground(theme.getMenuBg());
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.getBorder()));

        // Color button with preview
        JButton colorBtn = makeBtn("Color", theme);
        JPanel colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(20, 20));
        colorPreview.setBackground(drawColor);
        colorPreview.setBorder(BorderFactory.createLineBorder(theme.getBorder()));
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Pick Draw Color", drawColor);
            if (c != null) { drawColor = c; colorPreview.setBackground(c); isEraser = false; }
        });

        // Brush size
        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setForeground(theme.getForeground());
        sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 40, 1));
        sizeSpinner.setPreferredSize(new Dimension(50, 24));
        sizeSpinner.addChangeListener(e -> brushSize = (int) sizeSpinner.getValue());

        // Eraser
        JButton eraserBtn = makeBtn("Eraser", theme);
        eraserBtn.addActionListener(e -> { isEraser = !isEraser; eraserBtn.setText(isEraser ? "[Eraser ON]" : "Eraser"); });

        // Clear
        JButton clearBtn = makeBtn("Clear", theme);
        clearBtn.addActionListener(e -> {
            g2d.setColor(new Color(30, 30, 35));
            g2d.fillRect(0, 0, 680, 420);
            drawPanel.repaint();
        });

        // Save & Insert
        JButton saveBtn = new JButton("Save & Insert");
        saveBtn.setBackground(theme.getAccent());
        saveBtn.setForeground(new Color(15, 17, 21));
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveBtn.addActionListener(e -> {
            // Save as PNG
            String filename = "drawing_" + System.currentTimeMillis() + ".png";
            File outFile = new File(saveDir, filename);
            try {
                ImageIO.write(canvas, "png", outFile);
                savedFile = outFile;
                dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save drawing:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelBtn = makeBtn("Cancel", theme);
        cancelBtn.addActionListener(e -> dispose());

        toolbar.add(colorBtn);
        toolbar.add(colorPreview);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(sizeLabel);
        toolbar.add(sizeSpinner);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(eraserBtn);
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(saveBtn);
        toolbar.add(cancelBtn);

        add(drawPanel, BorderLayout.CENTER);
        add(toolbar, BorderLayout.SOUTH);
    }

    private void drawDot(int x, int y) {
        g2d.setColor(isEraser ? new Color(30, 30, 35) : drawColor);
        g2d.fillOval(x - brushSize/2, y - brushSize/2, brushSize, brushSize);
    }

    private JButton makeBtn(String text, Theme theme) {
        JButton b = new JButton(text);
        b.setBackground(theme.getSecondary());
        b.setForeground(theme.getForeground());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Returns the saved PNG file, or null if cancelled */
    public File getSavedFile() { return savedFile; }
}
