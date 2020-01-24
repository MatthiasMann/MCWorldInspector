package mcworldinspector;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTByteArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.IntPredicateBuilder;

/**
 *
 * @author matthias
 */
public class SubChunk12 implements SubChunk {

    private final GetBlockIndex gbi;
    private final byte globalY;
    private final int[] palette;
    private GlobalMapping mapping;
    private BlockColorMap.MappedBlockPalette mappedPalette = BlockColorMap.MappedBlockPalette.EMPTY;

    public SubChunk12(NBTByteArray blocks, NBTByteArray add, byte globalY) {
        this.gbi = (add == null) ? pos -> blocks.getUnsigned(pos)
                : pos -> blocks.getUnsigned(pos) | (add.getNibble(pos) << 8);
        this.globalY = globalY;
        final var bss = (add == null) ? 256 : 4096;
        final var bs = IntStream.range(0, 4096)
                .map(gbi::get)
                .collect(() -> new BitSet(bss), BitSet::set, BitSet::or);
        palette = new int[bs.cardinality()];
        for(int cnt=0,idx=-1 ; (idx=bs.nextSetBit(idx+1))>=0 ; cnt++)
            palette[cnt] = idx;
    }

    public void setGlobalMapping(GlobalMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public int getGlobalY() {
        return globalY & 255;
    }

    @Override
    public Stream<String> getBlockTypes() {
        if(mapping == null)
            return Stream.empty();
        final var pal = mapping.palette;
        return IntStream.of(palette)
                .mapToObj(value -> pal[value])
                .filter(Objects::nonNull);
    }

    @Override
    public NBTTagCompound getBlockFromPalette(int index) {
        if(mapping == null)
            return NBTTagCompound.EMPTY;
        final var block = mapping.palette[index];
        return (block != null)
                ? NBTTagCompound.of("Name", block)
                : NBTTagCompound.EMPTY;
    }

    @Override
    public int getBlockIndex(int xz, int y) {
        int idx = xz + ((y & 15) << 8);
        return gbi.get(idx);
    }

    @Override
    public byte getBlockType(int index) {
        if(index == 0)
            return AIR;
        if(mapping != null && index == mapping.water)
            return WATER;
        return NORMAL;
    }

    @Override
    public int getTopBlockIndexBelowLayer(int xz, int y, int ignoreMask) {
        do {
            final int index = gbi.get(y * 256 + xz);
            if((getBlockType(index) & ignoreMask) == 0)
                return index;
        } while (y-- > 0);
        return -1;
    }

    @Override
    public void mapBlockColors(BlockColorMap bcm) {
        mappedPalette = mapping == null
                ? BlockColorMap.MappedBlockPalette.EMPTY
                : mapping.mapBlockColors(bcm);
    }

    @Override
    public BlockColorMap.MappedBlockPalette mappedBlockColors() {
        return mappedPalette;
    }

    private static @FunctionalInterface interface GetBlockIndex {
        int get(int pos);
    }

    @Override
    public Stream<BlockInfo> findBlocks(List<String> blockTypes, BlockPos offset) {
        if(mapping == null)
            return Stream.empty();
        final var pal = mapping.palette;
        class Builder extends IntPredicateBuilder<Stream<BlockInfo>> {
            @Override
            public Stream<BlockInfo> build() {
                return Stream.empty();
            }
            @Override
            public Stream<BlockInfo> build(int index) {
                final var block = NBTTagCompound.of("Name", pal[index]);
                final var gbi = SubChunk12.this.gbi;
                return IntStream.range(0, 4096)
                        .filter(pos -> index == gbi.get(pos))
                        .mapToObj(pos -> new BlockInfo(pos, offset, block));
            }
            @Override
            public Stream<BlockInfo> build(int[] array, int count) {
                final var gbi = SubChunk12.this.gbi;
                return IntStream.range(0, 4096).mapToObj(pos -> {
                    final var value = gbi.get(pos);
                    for(int idx=0 ; idx<count ; ++idx)
                        if(array[idx] == value)
                            return new BlockInfo(pos, offset,
                                    NBTTagCompound.of("Name", pal[value]));
                    return null;
                }).filter(Objects::nonNull);
            }
        }
        final var b = new Builder();
        return IntPredicateBuilder.of(blockTypes.stream().flatMapToInt(
                blockType -> IntStream.range(0, pal.length).filter(
                        idx -> blockType.equals(pal[idx]))), b);
    }

    public static class GlobalMapping {
        final String[] palette = new String[1 << 12];
        final List<String> blockTypes;
        final int water;
        BlockColorMap lastBCM;
        BlockColorMap.MappedBlockPalette mbp;

        public GlobalMapping(NBTTagCompound level) {
            final var blockIds = level.getCompound("FML")
                    .getCompound("Registries")
                    .getCompound("minecraft:blocks")
                    .getList("ids", NBTTagCompound.class);
            blockIds.forEach(e -> {
                final var id = e.get("V", Integer.class);
                final var blockType = e.getString("K");
                if(id != null)
                    palette[id] = blockType;
            });
            blockTypes = blockIds.stream()
                    .map(e -> e.getString("K"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            water = blockIds.stream()
                    .filter(e -> "minecraft:water".equals(e.getString("K")))
                    .map(e -> e.get("V", Integer.class))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(-1);
        }

        BlockColorMap.MappedBlockPalette mapBlockColors(BlockColorMap bcm) {
            if(bcm != lastBCM) {
                lastBCM = bcm;
                mbp = bcm.map(palette);
            }
            return mbp;
        }

        public Stream<String> blockTypes() {
            return blockTypes.stream();
        }
    }
}
