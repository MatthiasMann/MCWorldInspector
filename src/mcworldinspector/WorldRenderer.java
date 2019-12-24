package mcworldinspector;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.AbstractListModel;
import javax.swing.JComponent;
import javax.swing.Timer;
import mcworldinspector.nbt.NBTTagCompound;

/**
 *
 * @author matthias
 */
public class WorldRenderer extends JComponent {

    private final HashSet<Chunk> chunks;
    private final int min_x;
    private final int min_z;
    private final int max_x;
    private final int max_z;
    private final HashMap<XZPosition, BufferedImage> images = new HashMap<>();

    private final ArrayList<HighlightEntry> highlights = new ArrayList<>();
    private final HighlightsModel highlights_model = new HighlightsModel(highlights);
    private final Timer highlight_timer;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public WorldRenderer(HashSet<Chunk> chunks) {
        this.chunks = chunks;
        this.min_x = chunks.parallelStream().mapToInt(Chunk::getGlobalX).reduce(Math::min).orElse(0);
        this.min_z = chunks.parallelStream().mapToInt(Chunk::getGlobalZ).reduce(Math::min).orElse(0);
        this.max_x = chunks.parallelStream().mapToInt(Chunk::getGlobalX).reduce(Math::max).orElse(0);
        this.max_z = chunks.parallelStream().mapToInt(Chunk::getGlobalZ).reduce(Math::max).orElse(0);
        highlight_timer = new Timer(1000, (e) -> {
            highlight_index = (highlight_index + 1) % HIGHLIGHT_COLORS.length;
            repaint();
        });
    }
    
    public void startChunkRendering() {
        // NOTE: must use single threaded worker as the BLOCK_TO_COLORMAP is shared
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            HashMap<XZPosition, ArrayList<Chunk>> regions = new HashMap<>();
            chunks.stream().forEach(chunk -> regions.computeIfAbsent(
                    chunk.getRegionStart(), pos -> new ArrayList<>()).add(chunk));
            regions.entrySet().forEach(e -> executor.submit(() -> {
                BufferedImage img = new BufferedImage(32*16, 32*16, BufferedImage.TYPE_BYTE_INDEXED, COLOR_MAP);
                e.getValue().forEach(chunk -> img.getRaster().setDataElements(
                        chunk.getLocalX()*16, chunk.getLocalZ()*16, 16, 16, renderChunk(chunk)));
                EventQueue.invokeLater(() -> {
                    final XZPosition p = e.getKey();
                    images.put(p, img);
                    repaint((p.x - min_x) * 16, (p.z - min_z) * 16, 32*16, 32*16);
                });
            }));
            executor.shutdown();
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                executor.shutdownNow();
            }
        });
    }

    public HighlightsModel getHighlightsModel() {
        return highlights_model;
    }

    public void highlightBlocks(List<String> blocks) {
        int oldCount = highlights.size();
        highlights.clear();
        highlights_model.fireIntervalRemoved(0, oldCount);
        if(!blocks.isEmpty()) {
            highlights.addAll(
                    chunks.parallelStream()
                    .filter(chunk -> chunk.getBlockTypes().anyMatch(blocks::contains))
                    .map(chunk -> new HighlightEntry(chunk))
                    .collect(Collectors.toList()));
        }
        if(highlights.isEmpty())
            highlight_timer.stop();
        else {
            highlights_model.fireIntervalAdded(0, highlights.size());
            highlight_timer.start();
        }
        repaint();
    }
    
    public void scrollTo(HighlightEntry e) {
        Rectangle r = new Rectangle(
                (e.getX() - min_x) * 16,
                (e.getZ() - min_z) * 16, 16, 16);
        r.grow(16, 16);
        scrollRectToVisible(r);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((max_x - min_x + 1) * 16, (max_z - min_z + 1) * 16);
    }

    private static Color HIGHLIGHT_COLORS[] = { Color.RED, Color.BLUE, Color.GREEN };
    private int highlight_index = 0;

    @Override
    protected void paintComponent(Graphics g) {
        images.entrySet().forEach(i -> {
            final XZPosition p = i.getKey();
            g.drawImage(i.getValue(), (p.x - min_x) * 16, (p.z - min_z) * 16, this);
        });
        g.setColor(HIGHLIGHT_COLORS[highlight_index]);
        highlights.forEach((h) -> {
            g.drawRect((h.getX() - min_x) * 16, (h.getZ() - min_z) * 16, 16, 16);
        });
    }

    static class WildcardEntry {
        final Pattern pattern;
        final byte index;

        public WildcardEntry(String pattern, byte index) {
            this.pattern = Pattern.compile(pattern);
            this.index = index;
        }
        public byte getIndex() {
            return index;
        }
    }

    static final HashMap<String, Byte> BLOCK_TO_COLORMAP = new HashMap<>();
    static final ArrayList<WildcardEntry> WILDCARDS = new ArrayList<>();
    static final IndexColorModel COLOR_MAP;

    static {
        int[] colorMapValues = new int[256];
        int nextIndex = 1;
        
        try(InputStream is=WorldRenderer.class.getResourceAsStream("blockmap.txt");
                InputStreamReader isr=new InputStreamReader(is);
                BufferedReader br=new BufferedReader(isr)) {
            String line;
            while((line=br.readLine()) != null) {
                line = line.trim();
                if(line.startsWith("#"))
                    continue;
                if(line.startsWith("x0"))
                    line = line.substring(2);
                try {
                    int color = Integer.parseInt(line.substring(0, 6), 16);
                    String name = line.substring(7);
                    if(name.startsWith("^"))
                        WILDCARDS.add(new WildcardEntry(name, (byte)nextIndex));
                    else
                        BLOCK_TO_COLORMAP.put(name, (byte)nextIndex);
                    colorMapValues[nextIndex++] = color;
                } catch(IllegalArgumentException ex) {
                }
            }
        } catch(IOException ex) {
        }

        COLOR_MAP = new IndexColorModel(8, colorMapValues.length, colorMapValues, 0, false, 0, DataBuffer.TYPE_BYTE);
    }

    private static byte[] renderChunk(Chunk chunk) {
        byte[] data = new byte[256];
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
    
    private static byte getBlockColorIndex(String name) {
        return BLOCK_TO_COLORMAP.computeIfAbsent(name, n -> {
            Optional<Byte> matched = WILDCARDS.stream().filter(
                    e -> e.pattern.matcher(n).matches())
                    .findAny().map(WildcardEntry::getIndex);
            if(!matched.isPresent())
                System.out.println("Unknown block type: " + n);
            return matched.orElse((byte)0);
        });
    }
    
    public static class HighlightEntry {
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

        @Override
        public String toString() {
            return "X=" + getX()*16 + " Z=" + getZ()*16;
        }
    }
    
    public static class HighlightsModel extends AbstractListModel<HighlightEntry> {
        final ArrayList<HighlightEntry> list;

        public HighlightsModel(ArrayList<HighlightEntry> list) {
            this.list = list;
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public HighlightEntry getElementAt(int index) {
            return list.get(index);
        }

        public void fireIntervalRemoved(int first, int last) {
            super.fireIntervalRemoved(this, first, last);
        }

        protected void fireIntervalAdded(int first, int last) {
            super.fireIntervalAdded(this, first, last);
        }
    }
}
