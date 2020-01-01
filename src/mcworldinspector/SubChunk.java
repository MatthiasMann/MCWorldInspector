package mcworldinspector;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
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

    public static final byte NORMAL = 0;
    public static final byte AIR = 1;
    public static final byte WATER = 2;

    private final NBTTagList<NBTTagCompound> palette;
    private final NBTLongArray blockStates;
    private final byte[] blockTypes;
    private final byte globalY;
    private final byte bits_per_blockstate;
    private BlockColorMap.MappedBlockPalette mappedPalette = BlockColorMap.MappedBlockPalette.EMPTY;

    public SubChunk(NBTTagList<NBTTagCompound> palette, NBTLongArray blockStates, byte globalY) {
        this.palette = palette;
        this.blockStates = blockStates;
        this.blockTypes = new byte[palette.size()];
        this.bits_per_blockstate = (byte)Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size()-1));
        this.globalY = globalY;

        for(int idx=0 ; idx<palette.size() ; ++idx) {
            String name = palette.get(idx).getString("Name");
            switch (name) {
                case "minecraft:cave_air":
                case "minecraft:air":
                    blockTypes[idx] = AIR;
                    break;
                case "minecraft:water":
                case "minecraft:bubble_column":
                    blockTypes[idx] = WATER;
                    break;
            }
        }
    }

    public int getGlobalY() {
        return globalY & 255;
    }

    public Stream<String> getBlockTypes() {
        return palette.stream().map(e -> e.getString("Name"));
    }
    
    public int getBlockIndex(int xz, int y) {
        final int bits = bits_per_blockstate & 255;
        return blockStates.getBits((xz + ((y & 15) << 8)) * bits, bits);
    }

    public NBTTagCompound getBlockFromPalette(int index) {
        return (index >= 0 && index < palette.size()) ? palette.get(index) : null;
    }

    public byte getBlockType(int index) {
        return (index >= 0 && index < blockTypes.length) ? blockTypes[index] : NORMAL;
    }

    public byte getBlockType(int xz, int y) {
        return getBlockType(getBlockIndex(xz, y));
    }

    public int getTopBlockIndexBelowLayer(int xz, int y, int ignoreMask) {
        final int bits = bits_per_blockstate & 255;
        final NBTLongArray bs = blockStates;
        do {
            final int index = bs.getBits((y * 256 + xz) * bits, bits);
            if((getBlockType(index) & ignoreMask) == 0)
                return index;
        } while (y-- > 0);
        return -1;
    }

    public void mapBlockColors(BlockColorMap bcm) {
        mappedPalette = bcm.map(palette);;
    }

    public BlockColorMap.MappedBlockPalette mappedBlockColors() {
        return mappedPalette;
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
                    .append("> = ");
            return blockToString(block, sb).toString();
        }

        public static StringBuilder blockToString(NBTTagCompound block, StringBuilder sb) {
            sb.append(block.getString("Name"));
            final NBTTagCompound properties = block.getCompound("Properties");
            if(!properties.isEmpty()) {
                properties.entries().forEachOrdered(
                        new Consumer<Map.Entry<String, Object>>() {
                            String sep = "{";
                            @Override
                            public void accept(Map.Entry<String, Object> p) {
                                sb.append(sep).append(p.getKey()).append('=')
                                        .append(Objects.toString(p.getValue()));
                                sep = ", ";
                            }
                        });
                sb.append('}');
            }
            return sb;
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
