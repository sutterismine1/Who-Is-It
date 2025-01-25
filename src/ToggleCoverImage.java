import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ToggleCoverImage extends JPanel {

    private BufferedImage image;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean isCoverVisible = false; // Track the visibility of the cover

    public ToggleCoverImage(BufferedImage image, int x, int y, int width, int height) {
        this.image = image;
        setBounds(x, y, width, height);
        // Add a mouse click listener to toggle the cover
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                isCoverVisible = !isCoverVisible; // Toggle the cover state
                repaint(); // Repaint the panel
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw the image
        if (image != null) {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
        }

        // Draw the transparent red cover if visible
        if (isCoverVisible) {
            g.setColor(new Color(255, 0, 0, 128)); // Red with 50% transparency
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}