package mcworldinspector;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
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

    public int getGlobalY() {
        return globalY & 255;
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
    
    public static class BlockInfo extends BlockPos {
        public final NBTTagCompound block;

        public BlockInfo(int x, int y, int z, NBTTagCompound block) {
            super(x, y, z);
            this.block = block;
        }

        BlockInfo(int pos, BlockPos offset, NBTTagCompound block) {
            super((pos & 15) + offset.x, (pos >> 8) + offset.y, ((pos >> 4) & 15) + offset.z);
            this.block = block;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('<').append(x)
                    .append(", ").append(y)
                    .append(", ").append(z)
                    .append("> = ").append(block.getString("Name"));
            final NBTTagCompound properties = block.getCompound("Properties");
            if(!properties.isEmpty()) {
                String sep = "{";
                for(Map.Entry<String, Object> p : properties.entrySet()) {
                    sb.append(sep).append(p.getKey()).append('=').append(Objects.toString(p.getValue()));
                    sep = ", ";
                }
                sb.append('}');
            }
            return sb.toString();
        }
    }

    private int findBlockTypeIndex(int start, String blockType) {
        for(int idx=start ; idx<palette.size() ; idx++) {
            if(blockType.equals(palette.get(idx).getString("Name")))
                return idx;
        }
        return -1;
    }

    public Stream<BlockInfo> findBlocks(String blockType, BlockPos offset) {
        final int bits = bits_per_blockstate & 255;
        final NBTLongArray bs = blockStates;
        final int index = findBlockTypeIndex(0, blockType);
        if(index < 0)
            return Stream.empty();
        int index2 = findBlockTypeIndex(index+1, blockType);
        if(index2 < 0) {
            final NBTTagCompound block = palette.get(index);
            return IntStream.range(0, 4096)
                    .filter(pos -> index == bs.getBits(pos*bits, bits))
                    .mapToObj(pos -> new BlockInfo(pos, offset, block));
        }
        final BitSet set = new BitSet();
        do {
            set.set(index2);
            index2 = findBlockTypeIndex(index2+1, blockType);
        } while(index2 >= 0);
        return IntStream.range(0, 4096)
                .mapToObj(pos -> {
                    int value = bs.getBits(pos*bits, bits);
                    if(!set.get(value))
                        return null;
                    return new BlockInfo(pos, offset, palette.get(value));
                })
                .filter(Objects::nonNull);
    }
}
