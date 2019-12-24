package mcworldinspector;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class Chunk extends XZPosition {
    
    private final NBTTagCompound nbt;
    private final NBTTagList<NBTTagCompound> sections;
    private final NBTLongArray heightmap;
    private final SubChunk[] subchunks = new SubChunk[16];

    public Chunk(int globalX, int globalZ, NBTTagCompound nbt) {
        super(globalX, globalZ);
        this.nbt = nbt;
        NBTTagCompound level = nbt.getCompound("Level");
        this.heightmap = level.getCompound("Heightmaps").get("MOTION_BLOCKING_NO_LEAVES", NBTLongArray.class);
        this.sections = level.getList("Sections", NBTTagCompound.class);
        if(sections != null) {
            for(NBTTagCompound s : sections) {
                int y = ((Number)s.get("Y")).intValue();
                NBTTagList<NBTTagCompound> palette = s.getList("Palette", NBTTagCompound.class);
                NBTLongArray blockStates = s.get("BlockStates", NBTLongArray.class);
                if(y >= 0 && y < subchunks.length && palette != null &&
                        blockStates != null && !blockStates.isEmpty())
                    subchunks[y] = new SubChunk(palette, blockStates, (byte)y);
            }
        }
    }

    public int getGlobalX() {
        return x;
    }

    public int getGlobalZ() {
        return z;
    }
    
    public int getLocalX() {
        return x & 31;
    }
    
    public int getLocalZ() {
        return z & 31;
    }
    
    public XZPosition getRegionStart() {
        return new XZPosition(x & ~31, z & ~31);
    }

    public SubChunk getSubChunk(int y) {
        return subchunks[y];
    }
    
    public NBTTagCompound getTopBlock(int x, int z) {
        if(heightmap != null) {
            int top = heightmap.getBits((z*16+x)*9, 9) - 1;
            if(top >= 0 && top < 256 && subchunks[top >> 4] != null)
                return subchunks[top >> 4].getBlock(x, top & 15, z);
        }
        return null;
    }

    public Stream<SubChunk.BlockPos> findBlocks(String blockType) {
        return subChunks().flatMap(sc -> sc.findBlocks(blockType));
    }
    
    public void forEach(Consumer<SubChunk> c) {
        for(SubChunk sc : subchunks) {
            if(sc != null)
                c.accept(sc);
        }
    }
    
    public Stream<SubChunk> subChunks() {
        return Arrays.stream(subchunks).filter(Objects::nonNull);
    }

    public Stream<String> getBlockTypes() {
        return subChunks().flatMap(SubChunk::getBlockTypes);
    }
}
