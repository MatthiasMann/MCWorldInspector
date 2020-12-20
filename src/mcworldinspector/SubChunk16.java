package mcworldinspector;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.utils.IntPredicateBuilder;

/**
 *
 * @author matthias
 */
public class SubChunk16 implements SubChunk {

    private final NBTTagList<NBTTagCompound> palette;
    private final NBTLongArray blockStates;
    private final byte[] blockTypes;
    private final byte globalY;
    private final byte bits_per_blockstate;
    private final byte blocks_per_long;
    private BlockColorMap.MappedBlockPalette mappedPalette = BlockColorMap.MappedBlockPalette.EMPTY;

    public SubChunk16(NBTTagList<NBTTagCompound> palette, NBTLongArray blockStates, byte globalY) {
        this.palette = palette;
        this.blockStates = blockStates;
        this.blockTypes = new byte[palette.size()];
        this.bits_per_blockstate = (byte)Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size()-1));
        this.blocks_per_long = (byte)(64 / (bits_per_blockstate & 255));
        this.globalY = globalY;

        if (blockStates.size() != (4095 + blocks_per_long) / blocks_per_long)
            throw new IllegalArgumentException("blocks_per_long doesn't match with blockState length");

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

    @Override
    public int getGlobalY() {
        return globalY & 255;
    }

    @Override
    public Stream<String> getBlockTypes() {
        return palette.stream().map(e -> e.getString("Name"));
    }

    private int getBlockIndex(int pos) {
        final int bits = bits_per_blockstate & 255;
        final int bpl = blocks_per_long & 255;
        return (int) (blockStates.get(pos / bpl) >>> (bits * (pos % bpl))) & ((1 << bits) - 1);
    }

    @Override
    public int getBlockIndex(int xz, int y) {
        return getBlockIndex(xz + ((y & 15) << 8));
    }

    @Override
    public NBTTagCompound getBlockFromPalette(int index) {
        return (index >= 0 && index < palette.size()) ? palette.get(index) : null;
    }

    @Override
    public byte getBlockType(int index) {
        return (index >= 0 && index < blockTypes.length) ? blockTypes[index] : NORMAL;
    }

    @Override
    public int getTopBlockIndexBelowLayer(int xz, int y, int ignoreMask) {
        do {
            final int index = getBlockIndex(xz, y);
            if((getBlockType(index) & ignoreMask) == 0)
                return index;
        } while (y-- > 0);
        return -1;
    }

    @Override
    public void mapBlockColors(BlockColorMap bcm) {
        mappedPalette = bcm.map(palette);
    }

    @Override
    public BlockColorMap.MappedBlockPalette mappedBlockColors() {
        return mappedPalette;
    }

    @Override
    public Stream<BlockInfo> findBlocks(List<String> blockTypes, BlockPos offset) {
        class Builder extends IntPredicateBuilder<Stream<BlockInfo>> {
            @Override
            public Stream<BlockInfo> build() {
                return Stream.empty();
            }
            @Override
            public Stream<BlockInfo> build(int index) {
                final var block = palette.get(index);
                return IntStream.range(0, 4096)
                        .filter(pos -> index == getBlockIndex(pos))
                        .mapToObj(pos -> new BlockInfo(pos, offset, block));
            }
            @Override
            public Stream<BlockInfo> build(int[] array, int count) {
                final var pal = palette;
                return IntStream.range(0, 4096).mapToObj(pos -> {
                    final var value = getBlockIndex(pos);
                    for(int idx=0 ; idx<count ; ++idx)
                        if(array[idx] == value)
                            return new BlockInfo(pos, offset, pal.get(value));
                    return null;
                }).filter(Objects::nonNull);
            }
        }
        final var b = new Builder();
        final var pal = palette;
        return IntPredicateBuilder.of(blockTypes.stream().flatMapToInt(
                blockType -> IntStream.range(0, pal.size()).filter(
                        idx -> blockType.equals(pal.get(idx).getString("Name")))), b);
    }
}
