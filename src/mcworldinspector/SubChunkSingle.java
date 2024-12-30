package mcworldinspector;

import java.util.List;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class SubChunkSingle implements SubChunk {
    
    private final NBTTagList<NBTTagCompound> palette;
    private final short globalY;
    private final byte[] blockTypes;
    private BlockColorMap.MappedBlockPalette mappedPalette = BlockColorMap.MappedBlockPalette.EMPTY;
    
    public SubChunkSingle(NBTTagList<NBTTagCompound> palette, short globalY) {
        this.palette = palette;
        this.globalY = globalY;
        this.blockTypes = SubChunk.createBlockTypes(palette);
    }
    
    @Override
    public int getGlobalY() {
        return globalY;
    }

    @Override
    public Stream<String> getBlockTypes() {
        return palette.stream().map(e -> e.getString("Name"));
    }

    @Override
    public byte getBlockType(int index) {
        return (index >= 0 && index < blockTypes.length) ? blockTypes[index] : NORMAL;
    }

    @Override
    public NBTTagCompound getBlockFromPalette(int index) {
        return (index >= 0 && index < palette.size()) ? palette.get(index) : null;
    }

    @Override
    public int getBlockIndex(int xz, int y) {
        return 0;
    }

    @Override
    public int getTopBlockIndexBelowLayer(int xz, int y, int ignoreMask) {
        if((getBlockType(0) & ignoreMask) == 0)
            return 0;
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
        return Stream.empty();
    }
    
}
