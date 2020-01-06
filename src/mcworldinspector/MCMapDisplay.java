package mcworldinspector;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.TranslatedIcon;

/**
 *
 * @author matthias
 */
public class MCMapDisplay extends JComponent {

    private static final HashMap<Byte, Icon> MAP_MARKERS = new HashMap<>();

    private MCMap map;
    private ImageIcon backgroundImage;
    private BufferedImage mapImage;
    private List<Icon> decorations = Collections.emptyList();

    public MCMapDisplay() {
        final var bgURL = MCMapDisplay.class.getResource("map_background.png");
        backgroundImage = (bgURL != null) ? new ImageIcon(bgURL) : null;
    }

    public MCMap getMap() {
        return map;
    }

    public void setMap(MCMap map) {
        this.map = map;
        if(map != null) {
            this.mapImage = map.createImage();
            this.decorations = map.getDecorations().stream()
                    .map(this::createDecorationIcon)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            this.mapImage = null;
            this.decorations = Collections.emptyList();
        }
        repaint();
    }

    private Icon createDecorationIcon(NBTTagCompound decoration) {
        final var decoX = decoration.get("x", Double.class);
        final var decoZ = decoration.get("z", Double.class);
        final var type = decoration.get("type", Byte.class);
        if(type == null || decoX == null || decoZ == null)
            return null;
        final var icon = getMapMarkerIcon(type);
        if(icon == null)
            return null;
        final var scale = map.getScale();
        final var posX = ((int)(decoX - map.getX()) >> scale) + 64;
        final var posZ = ((int)(decoZ - map.getZ()) >> scale) + 64;
        System.out.println("posX="+posX+" posZ="+posZ);
        return new TranslatedIcon(icon,
                posX - icon.getIconWidth() / 2,
                posZ - icon.getIconHeight() / 2);
    }

    private static synchronized Icon getMapMarkerIcon(byte type) {
        return MAP_MARKERS.computeIfAbsent(type, t -> {
            final var url = MapsPanel.class.getResource(
                    "map_marker_" + t + ".png");
            return url != null ? new ImageIcon(url) : null;
        });
    }

    @Override
    public Dimension getPreferredSize() {
        final var insets = getInsets();
        final var width = backgroundImage != null ? backgroundImage.getIconWidth() : 128;
        final var height = backgroundImage != null ? backgroundImage.getIconHeight() : 128;
        return new Dimension(
                insets.left + width + insets.right,
                insets.top + height + insets.bottom);
    }

    @Override
    protected void paintComponent(Graphics g) {
        final var insets = getInsets();
        final int x;
        final int y;
        if(backgroundImage != null) {
            backgroundImage.paintIcon(this, g, insets.left, insets.top);
            x = insets.left + (backgroundImage.getIconWidth() - 128) / 2;
            y = insets.top + (backgroundImage.getIconHeight() - 128) / 2;
        } else {
            x = insets.left;
            y = insets.top;
        }
        if(map != null)
            g.drawImage(mapImage, x, y, this);
        decorations.forEach(i -> i.paintIcon(this, g, x, y));
    }

}
