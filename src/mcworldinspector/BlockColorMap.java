package mcworldinspector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.BitSet;
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
        public final boolean tinting;

        public BlockColorInfo(int color, boolean tinting) {
            this.color = color;
            this.tinting = tinting;
        }
    }
    
    public static class MappedBlockPalette {
        public static final MappedBlockPalette EMPTY = new MappedBlockPalette(0);

        private final int[] colors;
        private final BitSet tinting;

        private MappedBlockPalette(int size) {
            this.colors = new int[size];
            this.tinting = new BitSet(size);
        }
        
        public int getColor(int index) {
            return (index < colors.length) ? colors[index] : 0;
        }
        
        public boolean needsTinting(int index) {
            return tinting.get(index);
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

    public MappedBlockPalette map(NBTTagList<NBTTagCompound> palette) {
        MappedBlockPalette m = new MappedBlockPalette(palette.size());
        for(int idx=0 ; idx<palette.size() ; ++idx) {
            NBTTagCompound block = palette.get(idx);
            String name = block.getString("Name");
            BlockColorInfo bci = blocks.get(name);
            if(bci != null) {
                m.colors[idx] = bci.color;
                if(bci.tinting)
                    m.tinting.set(idx);
            }
        }
        return m;
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
                    boolean tinting = line.charAt(7) == '#';
                    String name = line.substring(tinting ? 8 : 7);
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
}
