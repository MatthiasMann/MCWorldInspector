package mcworldinspector;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class HighlightEntry {
    public final Chunk chunk;

    public HighlightEntry(Chunk chunk) {
        this.chunk = chunk;
    }

    public int getX() {
        return chunk.getGlobalX();
    }

    public int getZ() {
        return chunk.getGlobalZ();
    }

    public boolean contains(Point p) {
        return getX() == (p.x >> 4) && getZ() == (p.y >> 4);
    }

    @Override
    public String toString() {
        final int x = getX() << 4;
        final int z = getZ() << 4;
        return "Chunk <" + x + ", " + z + "> to <" + (x+15) + ", " + (z+15) + '>';
    }
    
    public void paint(Graphics g, int zoom) {
        final int zoom16 = 16 * zoom;
        g.fillRect(getX() * zoom16, getZ() * zoom16, zoom16, zoom16);
    }

    public static class WithOverlay extends HighlightEntry {
        private BufferedImage overlay;

        public WithOverlay(Chunk chunk) {
            super(chunk);
        }

        public WithOverlay(Chunk chunk, BufferedImage overlay) {
            super(chunk);
            this.overlay = overlay;
        }
        
        public void setRGB(int x, int z, int color) {
            if(overlay == null)
                overlay = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            overlay.setRGB(x, z, color);
        }

        public Stream<HighlightEntry> stream() {
            if(overlay == null)
                return Stream.empty();
            return Stream.of(this);
        }

        @Override
        public void paint(Graphics g, int zoom) {
            if(zoom == 1)
                g.drawImage(overlay, getX() * 16, getZ() * 16, null);
            else {
                final int zoom16 = 16 * zoom;
                g.drawImage(overlay, getX() * zoom16, getZ() * zoom16,
                        zoom16, zoom16, null);
            }
        }
    }
}
