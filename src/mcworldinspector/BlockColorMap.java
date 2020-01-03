package mcworldinspector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class BlockColorMap {
    
    public static class BlockColorInfo {
        public final int color;
        public final int tinting;

        public BlockColorInfo(int color, int tinting) {
            this.color = color;
            this.tinting = tinting;
        }
    }
    
    public static class MappedBlockPalette {
        public static final MappedBlockPalette EMPTY =
                new MappedBlockPalette(new int[0], new byte[0]);

        private final int[] colors;
        private final byte[] tinting;

        private MappedBlockPalette(int[] colors, byte[] tinting) {
            this.colors = colors;
            this.tinting = tinting;
        }
        
        public int getColor(int index) {
            return (index < colors.length) ? colors[index] : 0;
        }
        
        public int getTinting(int index) {
            return (index < tinting.length) ? tinting[index] & 255 : 0;
        }
    }

    public static final BlockColorMap EMPTY = new BlockColorMap();

    private final HashMap<String, BlockColorInfo> blocks;

    private BlockColorMap() {
        blocks = new HashMap<>();
    }

    public BlockColorMap(Map<String, BlockColorInfo> blocks) {
        this.blocks = new HashMap<>(blocks);
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public MappedBlockPalette map(NBTTagList<NBTTagCompound> palette) {
        final int[] colors = new int[palette.size()];
        final byte[] tinting = new byte[palette.size()];
        for(int idx=0 ; idx<palette.size() ; ++idx) {
            NBTTagCompound block = palette.get(idx);
            String name = block.getString("Name");
            BlockColorInfo bci = blocks.get(name);
            if(bci != null) {
                colors[idx] = bci.color;
                tinting[idx] = (byte)bci.tinting;
            }
        }
        return new MappedBlockPalette(colors, tinting);
    }

    public static BlockColorMap load(InputStream is) throws IOException {
        try(InputStreamReader isr = new InputStreamReader(is, "UTF8");
                BufferedReader br = new BufferedReader(isr)) {
            BlockColorMap bcm = new BlockColorMap();
            br.lines().forEach(line -> {
                if(line.length() < 8)
                    return;
                try {
                    int color = Integer.parseInt(line.substring(0, 6), 16);
                    color |= 0xFF000000;    // set alpha to opaque
                    boolean hasTinting = line.charAt(7) == '#';
                    int tinting = hasTinting ? line.charAt(8) - '0' : 0;
                    String name = line.substring(hasTinting ? 9 : 7);
                    bcm.blocks.put(name, new BlockColorInfo(color, tinting));
                } catch(IllegalArgumentException ex) {
                }
            });
            return bcm;
        }
    }

    public static BlockColorMap load(File file) throws IOException {
        try(FileInputStream fis = new FileInputStream(file)) {
            return load(fis);
        }
    }

    public void save(PrintStream ps) {
        blocks.entrySet().stream().map(e -> {
            final int color = e.getValue().color & 0xFFFFFF;
            final int tinting = e.getValue().tinting;
            return (tinting > 0)
                    ? String.format("%06X #%d%s", color, tinting, e.getKey())
                    : String.format("%06X %s", color, e.getKey());
        }).forEach(ps::println);
    }

    public void save(File file) throws IOException {
        try(PrintStream ps = new PrintStream(file, "UTF8")) {
            save(ps);
        }
    }
}
