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
public class SubChunk14 implements SubChunk {

    private final NBTTagList<NBTTagCompound> palette;
    private final NBTLongArray blockStates;
    private final byte[] blockTypes;
    private final byte globalY;
    private final byte bits_per_blockstate;
    private BlockColorMap.MappedBlockPalette mappedPalette = BlockColorMap.MappedBlockPalette.EMPTY;

    public SubChunk14(NBTTagList<NBTTagCompound> palette, NBTLongArray blockStates, byte globalY) {
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

    @Override
    public int getGlobalY() {
        return globalY & 255;
    }

    @Override
    public Stream<String> getBlockTypes() {
        return palette.stream().map(e -> e.getString("Name"));
    }
    
    @Override
    public int getBlockIndex(int xz, int y) {
        final int bits = bits_per_blockstate & 255;
        return blockStates.getBits((xz + ((y & 15) << 8)) * bits, bits);
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
        final int bits = bits_per_blockstate & 255;
        final NBTLongArray bs = blockStates;
        do {
            final int index = bs.getBits((y * 256 + xz) * bits, bits);
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
                final var bits = bits_per_blockstate & 255;
                final var bs = blockStates;
                final var block = palette.get(index);
                return IntStream.range(0, 4096)
                        .filter(pos -> index == bs.getBits(pos*bits, bits))
                        .mapToObj(pos -> new BlockInfo(pos, offset, block));
            }
            @Override
            public Stream<BlockInfo> build(int[] array, int count) {
                final var pal = palette;
                final var bits = bits_per_blockstate & 255;
                final var bs = blockStates;
                return IntStream.range(0, 4096).mapToObj(pos -> {
                    final var value = bs.getBits(pos*bits, bits);
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
