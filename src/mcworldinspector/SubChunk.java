package mcworldinspector;

import java.util.BitSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class SubChunk {
    
    private final NBTTagList<NBTTagCompound> palette;
    private final NBTLongArray blockStates;
    private final byte globalY;
    private final byte bits_per_blockstate;
    private final byte air_index;
    private final byte cave_air_index;

    public SubChunk(NBTTagList<NBTTagCompound> palette, NBTLongArray blockStates, byte globalY) {
        this.palette = palette;
        this.blockStates = blockStates;
        this.bits_per_blockstate = (byte)Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size()-1));
        this.globalY = globalY;
        
        byte tmp_air_index = -1;
        byte tmp_cave_air_index = -1;
        for(int idx=0 ; idx<palette.size() ; ++idx) {
            String name = palette.get(idx).getString("Name");
            if("minecraft:cave_air".equals(name))
                tmp_cave_air_index = (byte)idx;
            if("minecraft:air".equals(name))
                tmp_air_index = (byte)idx;
        }
        this.air_index = tmp_air_index;
        this.cave_air_index = tmp_cave_air_index;
    }
    
    public Stream<String> getBlockTypes() {
        return palette.stream().map(e -> e.getString("Name"));
    }
    
    public int getBlockIndex(int x, int y, int z) {
        int bits = bits_per_blockstate & 255;
        return blockStates.getBits((y * 256 + z * 16 + x) * bits, bits);
    }

    public NBTTagCompound getBlock(int x, int y, int z) {
        return palette.get(getBlockIndex(x, y, z));
    }

    public NBTTagCompound getTopBlock(int x, int z) {
            for(int y=16 ; y-->0 ;) {
                int index = getBlockIndex(x, y, z);
                if(index != air_index && index != cave_air_index) {
                    try {
                        return palette.get(index);
                    } catch(IndexOutOfBoundsException ex) {
                        int pos = (y*256+z*16+x)*bits_per_blockstate;
                        System.err.println("x="+x+" y="+y+" z="+z+
                                " bits_per_blockstate="+bits_per_blockstate+
                                " bitpos="+(pos>>6)+":"+(pos&63));
                    }
                }
            }
        return null;
    }
    
    public static class BlockPos {
        public final int x;
        public final int y;
        public final int z;

        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "X=" + x + " Y=" + y + " Z=" + z;
        }
    }

    private int findBlockTypeIndex(int start, String blockType) {
        for(int idx=start ; idx<palette.size() ; idx++) {
            if(blockType.equals(palette.get(idx).getString("Name")))
                return idx;
        }
        return -1;
    }

    private IntStream findBlocksInt(String blockType) {
        final int bits = bits_per_blockstate & 255;
        final NBTLongArray bs = blockStates;
        final int index = findBlockTypeIndex(0, blockType);
        if(index < 0)
            return IntStream.empty();
        int index2 = findBlockTypeIndex(index+1, blockType);
        if(index2 < 0)
            return IntStream.range(0, 4096).filter(pos -> index == bs.getBits(pos*bits, bits));
        final BitSet set = new BitSet();
        do {
            set.set(index2);
            index2 = findBlockTypeIndex(index2+1, blockType);
        } while(index2 >= 0);
        return IntStream.range(0, 4096).filter(pos -> set.get(bs.getBits(pos*bits, bits)));
    }

    public Stream<BlockPos> findBlocks(String blockType) {
        return findBlocksInt(blockType).mapToObj(pos -> new BlockPos(pos & 15,
                                (pos >> 8) + (globalY & 255), (pos >> 4) & 15));
    }
}
