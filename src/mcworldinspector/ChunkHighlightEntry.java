package mcworldinspector;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import mcworldinspector.utils.StringHelpers;

/**
 *
 * @author matthias
 */
public class ChunkHighlightEntry implements WorldRenderer.HighlightEntry {
    public final Chunk chunk;

    public ChunkHighlightEntry(Chunk chunk) {
        this.chunk = chunk;
    }

    public int getX() {
        return chunk.getGlobalX() << 4;
    }

    public int getZ() {
        return chunk.getGlobalZ() << 4;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public String toString() {
        final int x = getX();
        final int z = getZ();
        return "Chunk <" + chunk.getGlobalX() + ", " + chunk.getGlobalZ() +
                "> from <" + x + ", " + z + "> to <" + (x+15) + ", " + (z+15) + '>';
    }

    public static Stream<ChunkHighlightEntry> of(Chunk chunk) {
        return chunk != null ? Stream.of(new ChunkHighlightEntry(chunk))
                : Stream.empty();
    }

    public static class WithOverlay extends ChunkHighlightEntry {
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

        public Stream<ChunkHighlightEntry> stream() {
            if(overlay == null)
                return Stream.empty();
            return Stream.of(this);
        }

        @Override
        public void paint(Graphics g, int zoom) {
            if(zoom == 1)
                g.drawImage(overlay, getX(), getZ(), null);
            else {
                final int zoom16 = 16 * zoom;
                g.drawImage(overlay, getX() * zoom, getZ() * zoom,
                        zoom16, zoom16, null);
            }
        }
    }
    
    public static class WithCount extends ChunkHighlightEntry implements Comparable<WithCount> {
        private final long count;

        public WithCount(Chunk chunk, long count) {
            super(chunk);
            this.count = count;
        }

        public long getCount() {
            return count;
        }

        @Override
        public String toString() {
            return super.toString() + " " + StringHelpers.formatCount(count, "Block");
        }

        @Override
        public int compareTo(WithCount o) {
            return Long.compare(o.count, count);
        }
    }
}
