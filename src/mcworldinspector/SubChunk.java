package mcworldinspector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public interface SubChunk {

    public static final byte NORMAL = 0;
    public static final byte AIR = 1;
    public static final byte WATER = 2;

    int getGlobalY();

    Stream<String> getBlockTypes();

    NBTTagCompound getBlockFromPalette(int index);

    int getBlockIndex(int xz, int y);

    byte getBlockType(int index);

    default byte getBlockType(int xz, int y) {
        return getBlockType(getBlockIndex(xz, y));
    }

    int getTopBlockIndexBelowLayer(int xz, int y, int ignoreMask);

    void mapBlockColors(BlockColorMap bcm);

    BlockColorMap.MappedBlockPalette mappedBlockColors();

    Stream<BlockInfo> findBlocks(List<String> blockTypes, BlockPos offset);

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
    
    public static byte[] createBlockTypes(NBTTagList<NBTTagCompound> palette) {
        byte[] blockTypes = new byte[palette.size()];
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
        return blockTypes;
    }
}
