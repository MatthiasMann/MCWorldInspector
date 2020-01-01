package mcworldinspector;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
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
import java.util.function.Function;
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
    /** NOTE: must use single threaded worker as the BLOCK_TO_COLORMAP is shared */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger asyncRenderingGeneration = new AtomicInteger();

    private List<HighlightEntry> highlights = Collections.emptyList();
    private final SimpleListModel<HighlightEntry> highlights_model = new SimpleListModel<>(highlights);
    private final Timer highlight_timer;
    private HighlightSelector highlightSelector;
    private HighlightEntry flash;
    private FlashMode flashMode = FlashMode.RESET;

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

    private Point getViewportCenter() {
        Container parent = getParent();
        if(parent instanceof JViewport) {
            Point pos = ((JViewport)parent).getViewPosition();
            pos.translate(parent.getWidth()/2 , parent.getHeight() / 2);
            return pos;
        }
        return new Point(getWidth()/2, getHeight()/2);
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
        center.translate(min_x * 16, min_z * 16);
        final int generation = asyncRenderingGeneration.incrementAndGet();
        executor.execute(() -> {
            HashMap<XZPosition, ArrayList<Chunk>> regions = new HashMap<>(
                    world.getChunks().size());
            world.chunks().forEach(chunk -> regions.computeIfAbsent(
                    chunk.getRegionStart(), p -> new ArrayList<>(32*32)).add(chunk));
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
                    repaint((p.x - min_x) * 16, (p.z - min_z) * 16, 32*16, 32*16);
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

    public HighlightSelector getHighlightSelector() {
        return highlightSelector;
    }

    public void highlight(HighlightSelector selector) {
        highlights_model.setList(Collections.emptyList());
        highlights = selector.apply(world).collect(Collectors.toList());
        if(highlights.isEmpty()) {
            highlightSelector = null;
            highlight_timer.stop();
        } else {
            highlightSelector = selector;
            highlights_model.setList(highlights);
            highlight_timer.start();
        }
        repaint();
    }

    public void scrollTo(Chunk chunk, boolean center) {
        Rectangle r = new Rectangle(
                (chunk.getGlobalX() - min_x) * 16,
                (chunk.getGlobalZ() - min_z) * 16, 16, 16);
        final Container parent = getParent();
        if(center && parent instanceof JViewport)
            r.grow((parent.getWidth() - r.width) / 2, (parent.getHeight() - r.height) / 2);
        else
            r.grow(16, 16);
        scrollRectToVisible(r);
    }

    public void scrollTo(HighlightEntry e) {
        scrollTo(e.chunk, false);
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

    public Point mouse2mc(Point p) {
        return new Point(p.x + min_x * 16, p.y+ min_z * 16);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((max_x - min_x + 1) * 16, (max_z - min_z + 1) * 16);
    }

    private static final Color HIGHLIGHT_COLORS[] = { Color.RED, Color.BLUE, Color.GREEN };
    private int highlight_index = 0;

    @Override
    protected void paintComponent(Graphics g) {
        final Rectangle clipBounds = g.getClipBounds();
        g.setColor(Color.BLACK);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        g.translate(-min_x*16, -min_z*16);

        images.entrySet().forEach(i -> {
            final XZPosition p = i.getKey();
            g.drawImage(i.getValue(), p.x * 16, p.z * 16, this);
        });
        g.setColor(HIGHLIGHT_COLORS[highlight_index]);
        highlights.forEach(h -> h.paint(g));
        if(flash != null) {
            if(flashMode != FlashMode.OFF) {
                g.setColor(Color.PINK);
                flash.paint(g);
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

    public static interface HighlightSelector extends Function<World, Stream<? extends HighlightEntry>> {
        default public void showDetailsFor(Component parent, HighlightEntry entry) {}
    }
}
