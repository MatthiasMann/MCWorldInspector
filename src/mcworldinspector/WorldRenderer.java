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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import mcworldinspector.nbt.NBTTagCompound;
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

    private List<HighlightEntry> highlights = Collections.EMPTY_LIST;
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
    
    public void startChunkRendering(Function<Chunk, int[]> chunkRenderer) {
        final int generation = asyncRenderingGeneration.incrementAndGet();
        executor.submit(() -> {
            HashMap<XZPosition, ArrayList<Chunk>> regions = new HashMap<>();
            world.chunks().forEach(chunk -> regions.computeIfAbsent(
                    chunk.getRegionStart(), pos -> new ArrayList<>()).add(chunk));
            regions.entrySet().forEach(e -> executor.submit(() -> {
                if(generation != asyncRenderingGeneration.get())
                    return;
                BufferedImage img = new BufferedImage(32*16, 32*16, BufferedImage.TYPE_INT_ARGB);
                e.getValue().forEach(chunk -> img.getRaster().setDataElements(
                        chunk.getLocalX()*16, chunk.getLocalZ()*16, 16, 16, chunkRenderer.apply(chunk)));
                EventQueue.invokeLater(() -> {
                    final XZPosition p = e.getKey();
                    images.put(p, img);
                    repaint((p.x - min_x) * 16, (p.z - min_z) * 16, 32*16, 32*16);
                });
            }));
        });
    }

    public SimpleListModel<HighlightEntry> getHighlightsModel() {
        return highlights_model;
    }

    public HighlightSelector getHighlightSelector() {
        return highlightSelector;
    }

    public void highlight(HighlightSelector selector) {
        highlights_model.setList(Collections.EMPTY_LIST);
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

    private static Color HIGHLIGHT_COLORS[] = { Color.RED, Color.BLUE, Color.GREEN };
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

    static class WildcardEntry {
        final Pattern pattern;
        final int color;

        public WildcardEntry(String pattern, int color) {
            this.pattern = Pattern.compile(pattern);
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    static final HashMap<String, Integer> BLOCK_TO_COLORMAP = new HashMap<>();
    static final ArrayList<WildcardEntry> WILDCARDS = new ArrayList<>();

    static {
        try(InputStream is=WorldRenderer.class.getResourceAsStream("blockmap.txt");
                InputStreamReader isr=new InputStreamReader(is);
                BufferedReader br=new BufferedReader(isr)) {
            br.lines().map(String::trim)
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> {
                if(line.startsWith("x0"))
                    line = line.substring(2);
                try {
                    int color = Integer.parseInt(line.substring(0, 6), 16);
                    color |= 0xFF000000;    // set alpha to opaque
                    String name = line.substring(7);
                    if(name.startsWith("^"))
                        WILDCARDS.add(new WildcardEntry(name, color));
                    else
                        BLOCK_TO_COLORMAP.put(name, color);
                } catch(IllegalArgumentException ex) {
                }
            });
        } catch(IOException ex) {
        }
    }

    public static int[] renderChunk(Chunk chunk) {
        int[] data = new int[256];
        for(int z=0 ; z<16 ; z++) {
            for(int x=0 ; x<16 ; x++) {
                NBTTagCompound block = chunk.getTopBlock(x, z);
                if(block != null) {
                    String name = block.getString("Name");
                    data[x+z*16] = getBlockColorIndex(name);
                }
            }
        }
        return data;
    }

    public static int[] renderChunkLayer(Chunk chunk, int y) {
        int[] data = new int[256];
        for(int z=0 ; z<16 ; z++) {
            for(int x=0 ; x<16 ; x++) {
                NBTTagCompound block = chunk.getCaveFloorBlock(x, y, z);
                if(block != null) {
                    String name = block.getString("Name");
                    data[x+z*16] = getBlockColorIndex(name);
                }
            }
        }
        return data;
    }
    
    private static int getBlockColorIndex(String name) {
        return BLOCK_TO_COLORMAP.computeIfAbsent(name, n -> {
            Optional<Integer> matched = WILDCARDS.stream().filter(
                    e -> e.pattern.matcher(n).matches())
                    .findAny().map(WildcardEntry::getColor);
            if(!matched.isPresent())
                System.out.println("Unknown block type: " + n);
            return matched.orElse(0);
        });
    }

    public static interface HighlightSelector extends Function<World, Stream<? extends HighlightEntry>> {
        default public void showDetailsFor(Component parent, HighlightEntry entry) {}
    }
}
