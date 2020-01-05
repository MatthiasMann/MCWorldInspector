package mcworldinspector;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Timer;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.utils.SimpleListModel;

/**
 *
 * @author matthias
 */
public class WorldRenderer extends JComponent {

    private final World world;
    private final int min_x;
    private final int min_z;
    private final int max_x;
    private final int max_z;
    private final HashMap<XZPosition, BufferedImage> images = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger asyncRenderingGeneration = new AtomicInteger();

    private List<HighlightEntry> highlights = Collections.emptyList();
    private final SimpleListModel<HighlightEntry> highlights_model = new SimpleListModel<>(highlights);
    private final Timer highlight_timer;
    private HighlightEntry flash;
    private FlashMode flashMode = FlashMode.RESET;
    private int zoom = 1;

    private enum FlashMode {
        RESET,
        ON,
        OFF
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public WorldRenderer(World world) {
        this.world = world;
        this.min_x = world.getChunks().parallelStream().mapToInt(Chunk::getGlobalX).reduce(Math::min).orElse(0);
        this.min_z = world.getChunks().parallelStream().mapToInt(Chunk::getGlobalZ).reduce(Math::min).orElse(0);
        this.max_x = world.getChunks().parallelStream().mapToInt(Chunk::getGlobalX).reduce(Math::max).orElse(0);
        this.max_z = world.getChunks().parallelStream().mapToInt(Chunk::getGlobalZ).reduce(Math::max).orElse(0);
        highlight_timer = new Timer(1000, (e) -> {
            highlight_index = (highlight_index + 1) % HIGHLIGHT_COLORS.length;
            repaint();
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                executor.shutdownNow();
            }
        });
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        if(this.zoom != zoom) {
            final Point center = getViewportCenter();
            this.zoom = zoom;
            revalidate();
            setViewportPos(center, null);
        }
    }

    public void setZoom(int zoom, Point p) {
        if(this.zoom != zoom) {
            final Point center = component2mc(p);
            final Point inViewport = viewportPosition(p);
            this.zoom = zoom;
            revalidate();
            setViewportPos(center, inViewport);
        }
    }

    private Point viewportPosition(Point p) {
        Container parent = getParent();
        if(parent instanceof JViewport) {
            final Point pos = ((JViewport)parent).getViewPosition();
            p.translate(-pos.x, -pos.y);
        }
        return p;
    }

    private Point getViewportCenter() {
        Point pos;
        Container parent = getParent();
        if(parent instanceof JViewport) {
            pos = ((JViewport)parent).getViewPosition();
            pos.translate(parent.getWidth() / 2, parent.getHeight() / 2);
        } else
            pos = new Point(getWidth() / 2, getHeight() / 2);
        return component2mc(pos);
    }

    private void setViewportPos(Point pos, Point inViewport) {
        Container parent = getParent();
        if(parent instanceof JViewport) {
            pos = mc2component(pos);
            if(inViewport != null)
                pos.translate(-inViewport.x, -inViewport.y);
            else
                pos.translate(parent.getWidth() / -2, parent.getHeight() / -2);
            if(!isValid())
                parent.validate();
            ((JViewport)parent).setViewPosition(pos);
        }
    }
    
    private static long getRegionDistance(XZPosition r, Point center) {
        long x = r.x * 16 + 16 * 16 - center.x;
        long z = r.z * 16 + 16 * 16 - center.y;
        return x*x + z*z;
    }

    public static @FunctionalInterface interface ChunkRenderer {
        public BufferedImage render(World world, ArrayList<Chunk> chunks);
    }

    public void setBlockColorMap(BlockColorMap bcm) {
        executor.execute(() -> world.chunks()
                .flatMap(Chunk::subChunks)
                .forEach(sc -> sc.mapBlockColors(bcm)));
    }

    public void startChunkRendering(ChunkRenderer chunkRenderer) {
        final Point center = getViewportCenter();
        final int generation = asyncRenderingGeneration.incrementAndGet();
        executor.execute(() -> {
            final var regions = world.chunks()
                    .collect(Collectors.groupingBy(Chunk::getRegionStart,
                            Collectors.toCollection(() -> new ArrayList<>(32*32))));
            regions.entrySet().stream().sorted((a,b) -> {
                return Long.compare(
                        getRegionDistance(a.getKey(), center),
                        getRegionDistance(b.getKey(), center));
            }).forEachOrdered(e -> executor.execute(() -> {
                if(generation != asyncRenderingGeneration.get())
                    return;
                final BufferedImage img = chunkRenderer.render(world, e.getValue());
                final XZPosition p = e.getKey();
                EventQueue.invokeLater(() -> {
                    images.put(p, img);
                    final int zoom16 = zoom * 16;
                    repaint((p.x - min_x) * zoom16, (p.z - min_z) * zoom16,
                            zoom16 * 32, zoom16 * 32);
                });
            }));
        });
    }

    public static BufferedImage renderChunksSurface(World world, ArrayList<Chunk> chunks, boolean withLeaves) {
        chunks.sort((a,b) -> {
            int diff = a.getLocalX() - b.getLocalX();
            if(diff == 0)
                diff = a.getLocalZ() - b.getLocalZ();
            return diff;
        });
        final Map<Integer, Biome> biomeRegistry = world.getBiomeRegistry();
        final BufferedImage img = new BufferedImage(32*16, 32*16, BufferedImage.TYPE_INT_ARGB);
        int[] prevY = new int[16];
        int prevX = -1;
        int prevZ = 0;
        for(int chunkIdx=0,numChunks=chunks.size() ; chunkIdx<numChunks ; chunkIdx++) {
            final Chunk chunk = chunks.get(chunkIdx);
            assert(!chunk.isEmpty());
            if(prevX != chunk.getLocalX() || prevZ + 1 != chunk.getLocalZ()) {
                Chunk aboveChunk;
                if(chunk.getLocalZ() == 0 && (aboveChunk = world.getChunk(
                        chunk.getGlobalX(), chunk.getGlobalZ() - 1)) != null &&
                        !aboveChunk.isEmpty())
                    Chunk.getHeights(aboveChunk.getHeightmap(withLeaves), 15, prevY);
                else
                    Arrays.fill(prevY, -1);
            }
            img.getRaster().setDataElements(
                    chunk.getLocalX()*16, chunk.getLocalZ()*16, 16, 16,
                    renderChunk(chunk, withLeaves, biomeRegistry, prevY));
            prevX = chunk.getLocalX();
            prevZ = chunk.getLocalZ();
        }
        return img;
    }

    public static BufferedImage renderChunksUnderground(World world, ArrayList<Chunk> chunks, int layer) {
        final Map<Integer, Biome> biomeRegistry = world.getBiomeRegistry();
        final BufferedImage img = new BufferedImage(32*16, 32*16, BufferedImage.TYPE_INT_ARGB);
        chunks.forEach(chunk-> {
            assert(!chunk.isEmpty());
            img.getRaster().setDataElements(
                    chunk.getLocalX()*16, chunk.getLocalZ()*16, 16, 16,
                    renderChunkLayer(chunk, biomeRegistry, layer));
        });
        return img;
    }

    public SimpleListModel<HighlightEntry> getHighlightsModel() {
        return highlights_model;
    }

    public void highlight(Stream<? extends HighlightEntry> highlights) {
        highlights_model.setList(Collections.emptyList());
        this.highlights = highlights.collect(Collectors.toList());
        if(this.highlights.isEmpty()) {
            highlight_timer.stop();
        } else {
            highlights_model.setList(this.highlights);
            highlight_timer.start();
        }
        repaint();
    }

    private void scrollTo(Rectangle r, boolean center) {
        r.translate(min_x * -16, min_z * -16);
        r.x *= zoom;
        r.y *= zoom;
        r.width *= zoom;
        r.height *= zoom;
        final Container parent = getParent();
        if(center && parent instanceof JViewport)
            r.grow((parent.getWidth() - r.width) / 2, (parent.getHeight() - r.height) / 2);
        else
            r.grow(zoom * 16, zoom * 16);
        scrollRectToVisible(r);
    }

    public void scrollTo(Chunk chunk, boolean center) {
        scrollTo(new Rectangle(chunk.getGlobalX() << 4, chunk.getGlobalZ() << 4,
                16, 16), center);
    }

    public void scrollTo(HighlightEntry e) {
        scrollTo(e.getRectangle(), false);
        flash = e;
        flashMode = FlashMode.RESET;
        highlight_timer.restart();
        repaint();
    }
    
    public void flash(HighlightEntry e) {
        flash = e;
        flashMode = FlashMode.ON;
        if (e == null)
            repaint();
    }

    public Point component2mc(Point p) {
        return new Point(p.x / zoom + min_x * 16, p.y / zoom + min_z * 16);
    }

    public Point mc2component(Point p) {
        return new Point((p.x - min_x * 16) * zoom, (p.y - min_z * 16) * zoom);
    }

    @Override
    public Dimension getPreferredSize() {
        final int zoom16 = zoom * 16;
        return new Dimension((max_x - min_x + 1) * zoom16,
                (max_z - min_z + 1) * zoom16);
    }

    private static final Color HIGHLIGHT_COLORS[] = { Color.RED, Color.BLUE, Color.GREEN };
    private int highlight_index = 0;

    @Override
    protected void paintComponent(Graphics g) {
        @SuppressWarnings("LocalVariableHidesMemberVariable")
        final int zoom = this.zoom;
        final int zoom16 = zoom * 16;
        final Rectangle clipBounds = g.getClipBounds();
        g.setColor(Color.BLACK);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        g.translate(-min_x*zoom16, -min_z*zoom16);

        images.entrySet().forEach((zoom == 1) ? e -> {
            final XZPosition p = e.getKey();
            g.drawImage(e.getValue(), p.x * 16, p.z * 16, this);
        } : e -> {
            final XZPosition p = e.getKey();
            g.drawImage(e.getValue(), p.x * zoom16, p.z * zoom16, zoom16 * 32, zoom16 * 32, this);
        });

        ((Graphics2D)g).setComposite(AlphaComposite.SrcOver.derive(0.4f));
        g.setColor(HIGHLIGHT_COLORS[highlight_index]);
        highlights.forEach(h -> h.paint(g, zoom));
        if(flash != null) {
            if(flashMode != FlashMode.OFF) {
                g.setColor(Color.PINK);
                flash.paint(g, zoom);
            }
            switch(flashMode) {
                case OFF: flashMode = FlashMode.ON; break;
                case ON: flashMode = FlashMode.OFF; break;
                case RESET: flash = null; break;
            }
        }
    }

    private static int[] renderChunk(Chunk chunk, boolean withLeaves, Map<Integer, Biome> biomeRegistry, int[] prevY) {
        final NBTIntArray biomes = chunk.getBiomes();
        final NBTLongArray heightmap = chunk.getHeightmap(withLeaves);
        final int[] data = new int[256];
        for(int idx=0 ; idx<256 ; idx++) {
            final int top = Chunk.getHeight(heightmap, idx) - 1;
            SubChunk sc;
            if(top >= 0 && top < 256 && (sc = chunk.getSubChunk(top >> 4)) != null) {
                final int index = sc.getBlockIndex(idx, top);
                if(index >= 0) {
                    int color = getBlockColor(sc, index, biomes, idx, biomeRegistry, top);
                    final int py = prevY[idx & 15];
                    if(py != top && py >= 0)
                        color = scaleRGB(color, (py < top)
                                ? COLOR_CHANGE_BRIGHTER
                                : COLOR_CHANGE_DARKER);
                    data[idx] = color;
                }
            }
            prevY[idx & 15] = top;
        }
        return data;
    }

    private static int[] renderChunkLayer(Chunk chunk, Map<Integer, Biome> biomeRegistry, int layer) {
        final NBTIntArray biomes = chunk.getBiomes();
        final int[] data = new int[256];
        chunk.forEachCaveFloorBlock(layer, (xz,y,sc,index) -> {
            int color = getBlockColor(sc, index, biomes, xz, biomeRegistry, y);
            data[xz] = color;
            return null;
        });
        return data;
    }

    private static final int COLOR_CHANGE_SHIFT    = 8;
    private static final int COLOR_CHANGE_BRIGHTER = (1 << COLOR_CHANGE_SHIFT) * 110 / 100;
    private static final int COLOR_CHANGE_DARKER   = (1 << COLOR_CHANGE_SHIFT) *  90 / 100;

    private static int scaleRGB(int color, int scale) {
        int r = (color >> 16) & 0xFF;
        int g = (color >>  8) & 0xFF;
        int b = (color      ) & 0xFF;
        r = Math.min(255, (r * scale) >> COLOR_CHANGE_SHIFT);
        g = Math.min(255, (g * scale) >> COLOR_CHANGE_SHIFT);
        b = Math.min(255, (b * scale) >> COLOR_CHANGE_SHIFT);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int getBlockColor(SubChunk sc, final int index, NBTIntArray biomes, int idx, Map<Integer, Biome> biomeRegistry, final int top) {
        BlockColorMap.MappedBlockPalette mbc = sc.mappedBlockColors();
        int color = mbc.getColor(index);
        int tinting = mbc.getTinting(index);
        if(tinting > 0)
            color = adjustColorByBiome(biomes, idx, biomeRegistry, top, tinting, color);
        return color;
    }

    private static int adjustColorByBiome(NBTIntArray biomes, int xz, Map<Integer, Biome> biomeRegistry, int y, int tinting, int color) {
        final Biome biome = (biomes != null && biomes.size() == 256)
                ? biomeRegistry.getOrDefault(biomes.getInt(xz), Biome.UNKNOWN)
                : Biome.UNKNOWN;
        int elevation = Math.max(0, y - 64);
        int biomeColor;
        switch (tinting) {
            case 1:
                biomeColor = biome.computeBiomeGrassColor(elevation);
                break;
            case 2:
                biomeColor = biome.computeBiomeFoilageColor(elevation);
                break;
            case 3:
                biomeColor = biome.waterColor;
                break;
            default:
                throw new AssertionError();
        }
        return mulColor(color, biomeColor);
    }

    private static int mulColor(int colorA, int colorB) {
        return (colorA & 0xFF000000) |
                ((((colorA >> 16) & 0xFF) * ((colorB >> 16) & 0xFF) / 255) << 16) |
                ((((colorA >>  8) & 0xFF) * ((colorB >>  8) & 0xFF) / 255) <<  8) |
                ((((colorA      ) & 0xFF) * ((colorB      ) & 0xFF) / 255)      );
    }

    public interface HighlightEntry {
        public int getX();
        public int getZ();
        public int getWidth();
        public int getHeight();
        public default Rectangle getRectangle() {
            return new Rectangle(getX(), getZ(), getWidth(), getHeight());
        }
        public default boolean contains(Point p) {
            final int x = getX();
            final int z = getZ();
            return (p.x >= x && p.x < x + getWidth()) &&
                    (p.y >= z && p.y < z + getHeight());
        }

        public default void paint(Graphics g, int zoom) {
            g.fillRect(getX() * zoom, getZ() * zoom,
                    getWidth() * zoom, getHeight() * zoom);
        }
        public default void showDetailsFor(Component parent) {}
    }
}
