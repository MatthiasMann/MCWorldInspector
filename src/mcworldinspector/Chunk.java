package mcworldinspector;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class Chunk extends XZPosition {

    private static final String HEIGHTMAPS = "Heightmaps";
    private static final String HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = "MOTION_BLOCKING_NO_LEAVES";
    private static final String HEIGHTMAP_MOTION_BLOCKING = "MOTION_BLOCKING";

    private final NBTTagCompound level;
    private final SubChunk[] subchunks = new SubChunk[16];

    public Chunk(int globalX, int globalZ, NBTTagCompound nbt) {
        super(globalX, globalZ);
        this.level = nbt.getCompound("Level");
        for(NBTTagCompound s : level.getList("Sections", NBTTagCompound.class)) {
            int y = ((Number)s.get("Y")).intValue();
            NBTTagList<NBTTagCompound> palette = s.getList("Palette", NBTTagCompound.class);
            NBTLongArray blockStates = s.get("BlockStates", NBTLongArray.class);
            if(y >= 0 && y < subchunks.length && !palette.isEmpty() &&
                    blockStates != null && !blockStates.isEmpty())
                subchunks[y] = new SubChunk(palette, blockStates, (byte)(y << 4));
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

    public boolean isSlimeChunk(long seed) {
        Random rnd = new Random(seed +
                (long) (x * x * 0x4c1906) +
                (long) (x * 0x5ac0db) +
                (long) (z * z) * 0x4307a7L +
                (long) (z * 0x5f24f) ^ 0x3ad8025f);
        return rnd.nextInt(10) == 0;
    }

    public boolean isEmpty() {
        final NBTTagCompound heightmaps = level.getCompound(HEIGHTMAPS);
        return heightmaps.isEmpty() ||
                heightmaps.get(HEIGHTMAP_MOTION_BLOCKING, NBTLongArray.class) == null ||
                heightmaps.get(HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES, NBTLongArray.class) == null;
    }

    public SubChunk getSubChunk(int y) {
        return subchunks[y];
    }

    public NBTIntArray getBiomes() {
        return level.get("Biomes", NBTIntArray.class);
    }
    
    public Biome getBiome(int xz, Map<Integer, Biome> biomeRegistry) {
        final NBTIntArray biomes = getBiomes();
        if(biomes != null && biomes.size() == 256)
            return biomeRegistry.get(biomes.getInt(xz));
        return null;
    }

    public Biome getBiome(int x, int z, Map<Integer, Biome> biomeRegistry) {
        return getBiome(z*16 + x, biomeRegistry);
    }

    public static @FunctionalInterface interface WrapBlock<R> {
        public R apply(int localXZ, int y, SubChunk sc, int index);
    }

    private WrapBlock<SubChunk.BlockInfo> makeBlockInfo()  {
        return (xz,y,sc,index) -> new SubChunk.BlockInfo(
                (x << 4) + (xz & 15), y, (z << 4) + (xz >> 4),
                sc.getBlockFromPalette(index));
    }

    public NBTLongArray getHeightmap(boolean withLeaves) {
        final NBTTagCompound heightmaps = level.getCompound(HEIGHTMAPS);
        return heightmaps.get(withLeaves ? HEIGHTMAP_MOTION_BLOCKING
                : HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES, NBTLongArray.class);
    }

    public static int getHeight(NBTLongArray heightmap, int xz) {
        return heightmap.getBits(xz*9, 9);
    }

    public static void getHeights(NBTLongArray heightmap, int z, int[] heights) {
        int bitIdx = z * 16 * 9;
        for(int xx=0 ; xx<16 ; xx++, bitIdx += 9)
            heights[xx] = heightmap.getBits(bitIdx, 9) - 1;
    }

    public<R> R getTopBlock(NBTLongArray heightmap, int xz, WrapBlock<R> wrap) {
        final int top = heightmap.getBits(xz*9, 9) - 1;
        SubChunk sc;
        if(top >= 0 && top < 256 && (sc = subchunks[top >> 4]) != null) {
            final int index = sc.getBlockIndex(xz, top);
            if(index >= 0)
                return wrap.apply(xz, top, sc, index);
        }
        return null;
    }

    public SubChunk.BlockInfo getTopBlockInfo(NBTLongArray heightmap, int x, int z) {
        return getTopBlock(heightmap, z*16 + x, makeBlockInfo());
    }

    public byte getBlockType(int xz, int y) {
        final SubChunk sc = subchunks[y >> 4];
        return (sc == null) ? SubChunk.AIR : sc.getBlockType(xz, y);
    }

    public<R> R getTopBlockBelowLayer(int xz, int y, int ignoreMask, WrapBlock<R> wrap) {
        for(;;) {
            final SubChunk sc = subchunks[y >> 4];
            if(sc != null) {
                final int index = sc.getTopBlockIndexBelowLayer(xz, y & 15, ignoreMask);
                if(index >= 0)
                    return wrap.apply(xz, y, sc, index);
            }
            if(y <= 15)
                return null;
            y = (y & ~15) - 1;
        }
    }

    public<R> R getCaveFloorBlock(int xz, int layer, WrapBlock<R> wrap) {
        if(layer <= 0)
            return null;
        byte blockType = getBlockType(xz, layer);
        return (blockType != SubChunk.NORMAL)
                 ? getTopBlockBelowLayer(xz, layer - 1,
                         blockType | SubChunk.AIR, wrap) : null;
    }

    public<R> void forEachCaveFloorBlock(int layer, WrapBlock<R> wrap) {
        if(layer <= 0)
            return;
        final SubChunk scAirCheck = subchunks[layer >> 4];
        if(scAirCheck != null) {
            if(layer <= 15) {
                for(int idx=0 ; idx<256 ; idx++) {
                    byte blockType = scAirCheck.getBlockType(idx, layer);
                    if(blockType != SubChunk.NORMAL) {
                        final int index = scAirCheck.
                                getTopBlockIndexBelowLayer(idx, layer - 1,
                                        blockType | SubChunk.AIR);
                        if(index >= 0)
                            wrap.apply(idx, layer - 1, scAirCheck, index);
                    }
                }
            } else {
                for(int idx=0 ; idx<256 ; idx++) {
                    byte blockType = scAirCheck.getBlockType(idx, layer);
                    if(blockType != SubChunk.NORMAL)
                        getTopBlockBelowLayer(idx, layer - 1,
                                blockType | SubChunk.AIR, wrap);
                }
            }
        } else if(layer >= 16) {
            for(int idx=0 ; idx<256 ; idx++)
                getTopBlockBelowLayer(idx, layer - 1, SubChunk.AIR, wrap);
        }
    }

    public SubChunk.BlockInfo getCaveFloorBlockInfo(int x, int layer, int z) {
        return getCaveFloorBlock(z*16 + x, layer, makeBlockInfo());
    }
    
    public Stream<SubChunk> subChunks() {
        return Arrays.stream(subchunks).filter(Objects::nonNull);
    }

    public Stream<SubChunk> subChunks(int from, int to) {
        return Arrays.stream(subchunks, from, to).filter(Objects::nonNull);
    }
    
    public Stream<NBTTagCompound> entities() {
        return level.getList("Entities", NBTTagCompound.class).stream();
    }

    public Stream<String> entityTypes() {
        return entities().flatMap(toID());
    }

    public Stream<NBTTagCompound> getEntities(String id) {
        return entities().filter(filterByID(id));
    }

    public Stream<NBTTagCompound> tileEntities() {
        return level.getList("TileEntities", NBTTagCompound.class).stream();
    }
    
    public Stream<String> tileEntityTypes() {
        return tileEntities().flatMap(toID());
    }

    public Stream<NBTTagCompound> getTileEntities(String id) {
        return tileEntities().filter(filterByID(id));
    }

    public Stream<NBTTagCompound> structures() {
        return level.getCompound("Structures").getCompound("Starts")
                .values(NBTTagCompound.class);
    }

    public Stream<String> structureTypes() {
        return structures().map(v -> v.getString("id"))
                .filter(id -> id != null && !"INVALID".equals(id));
    }
    
    public Stream<NBTTagCompound> getStructures(String id) {
        return structures().filter(filterByID(id));
    }

    public IntStream biomes() {
        final NBTIntArray biomes = getBiomes();
        return (biomes == null) ? IntStream.empty() : biomes.stream();
    }

    public Stream<Biome> biomes(Map<Integer, Biome> biomeRegistry) {
        return biomes().mapToObj(biomeRegistry::get).filter(Objects::nonNull);
    }

    private static Function<NBTTagCompound, Stream<String>> toID() {
        return e -> e.getStringAsStream("id");
    }

    private static Predicate<NBTTagCompound> filterByID(String id) {
        return v -> id.equals(v.getString("id"));
    }
}
