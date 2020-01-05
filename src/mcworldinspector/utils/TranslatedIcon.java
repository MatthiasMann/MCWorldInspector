package mcworldinspector.utils;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/**
 *
 * @author matthias
 */
public class TranslatedIcon implements Icon {

    private final Icon icon;
    private final int offsetX;
    private final int offsetY;

    public TranslatedIcon(Icon icon, int offsetX, int offsetY) {
        this.icon = icon;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        icon.paintIcon(c, g, x + offsetX, y + offsetY);
    }

    @Override
    public int getIconWidth() {
        return icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return icon.getIconHeight();
    }
}
