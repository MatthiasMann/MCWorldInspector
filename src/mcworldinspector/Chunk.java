package mcworldinspector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTByteArray;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class Chunk extends XZPosition {

    private static final String HEIGHTMAP = "HeightMap";
    private static final String HEIGHTMAPS = "Heightmaps";
    private static final String HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = "MOTION_BLOCKING_NO_LEAVES";
    private static final String HEIGHTMAP_MOTION_BLOCKING = "MOTION_BLOCKING";

    private final NBTTagCompound level;
    private final SubChunk[] subchunks = new SubChunk[16];

    public Chunk(int globalX, int globalZ, NBTTagCompound nbt) {
        super(globalX, globalZ);
        final int dataVersion = nbt.get("DataVersion", Integer.class);
        this.level = nbt.getCompound("Level");
        for(NBTTagCompound s : level.getList("Sections", NBTTagCompound.class)) {
            int y = ((Number)s.get("Y")).intValue();
            if(y >= 0 && y < subchunks.length) {
                NBTTagList<NBTTagCompound> palette = s.getList("Palette", NBTTagCompound.class);
                NBTLongArray blockStates = s.get("BlockStates", NBTLongArray.class);
                if(!palette.isEmpty() && blockStates != null && !blockStates.isEmpty())
                    if (dataVersion >= 0xA18)
                        subchunks[y] = new SubChunk16(palette, blockStates, (byte)(y << 4));
                    else
                        subchunks[y] = new SubChunk14(palette, blockStates, (byte)(y << 4));
                else {
                    NBTByteArray blocks = s.get("Blocks", NBTByteArray.class);
                    NBTByteArray add = s.get("Add", NBTByteArray.class);
                    if(blocks != null && add != null)
                        subchunks[y] = new SubChunk12(blocks, add, (byte)(y << 4));
                }
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

    public NBTTagCompound getLevel() {
        return level;
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
        return getHeightmap(true) == null;
    }

    public SubChunk getSubChunk(int y) {
        return subchunks[y];
    }

    public Biomes getBiomes() {
        final var biomes = level.get("Biomes");
        if(biomes instanceof NBTIntArray)
            return Biomes.of((NBTIntArray)biomes);
        if(biomes instanceof NBTByteArray)
            return Biomes.of((NBTByteArray)biomes);
        return null;
    }

    public static @FunctionalInterface interface WrapBlock<R> {
        public R apply(int localXZ, int y, SubChunk sc, int index);
    }

    private WrapBlock<SubChunk.BlockInfo> makeBlockInfo()  {
        return (xz, y, sc, index) -> {
            NBTTagCompound block = sc.getBlockFromPalette(index);
            if (block == null)
                return null;
            return new SubChunk.BlockInfo(
                    (x << 4) + (xz & 15), y, (z << 4) + (xz >> 4),
                    block);
        };
    }

    public HeightMap getHeightmap(boolean withLeaves) {
        final NBTTagCompound heightmaps = level.getCompound(HEIGHTMAPS);
        if(heightmaps.isEmpty())
            return HeightMap.of(level.get(HEIGHTMAP, NBTIntArray.class));
        return HeightMap.of(heightmaps.get(withLeaves
                ? HEIGHTMAP_MOTION_BLOCKING
                : HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES, NBTLongArray.class));
    }

    public<R> R getTopBlock(HeightMap heightmap, int xz, WrapBlock<R> wrap) {
        if(heightmap == null)
            return null;
        final int top = heightmap.getHeight(xz) - 1;
        SubChunk sc;
        if(top >= 0 && top < 256 && (sc = subchunks[top >> 4]) != null) {
            final int index = sc.getBlockIndex(xz, top);
            if(index >= 0)
                return wrap.apply(xz, top, sc, index);
        }
        return null;
    }

    public SubChunk.BlockInfo getTopBlockInfo(HeightMap heightmap, int x, int z) {
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
        final var biomes = getBiomes();
        return (biomes == null) ? IntStream.empty() : biomes.stream();
    }

    public Stream<Biome> biomes(Map<Integer, Biome> biomeRegistry) {
        return biomes().mapToObj(biomeRegistry::get).filter(Objects::nonNull);
    }

    private static Function<NBTTagCompound, Stream<String>> toID() {
        return e -> e.getStringAsStream("id");
    }

    public static Predicate<NBTTagCompound> filterByID(String id) {
        return v -> id.equals(v.getString("id"));
    }

    public static Predicate<NBTTagCompound> filterByID(Collection<String> ids) {
        return v -> {
            final var id = v.getString("id");
            return id != null && ids.contains(id);
        };
    }

    public @FunctionalInterface interface HeightMap {
        public int getHeight(int xz);
        
        default void getHeights(int z, int[] heights) {
            for(int x=0 ; x<16 ; x++)
                heights[x] = getHeight((z << 4) | x) - 1;
        }

        public static HeightMap of(NBTLongArray a) {
            if(a == null)
                return null;
            switch(a.size()) {
                case 36:
                    return xz -> a.getBits(xz * 9, 9);
                case 37:
                    return xz -> (int)(a.get(xz / 7) >>> (9*(xz % 7))) & 0x1FF;
                default:
                    System.err.println("Unknown Heightmap format with length " + a.size());
                    return null;
            }
        }
        public static HeightMap of(NBTIntArray a) {
            if(a == null || a.size() != 256)
                return null;
            return xz -> a.getInt(xz);
        }
    }

    public interface Biomes {
        public int getBiome(int xz);

        public IntStream stream();

        default Biome getBiome(int xz, Map<Integer, Biome> biomeRegistry) {
            return biomeRegistry.get(getBiome(xz));
        }

        default Biome getBiome(int x, int z, Map<Integer, Biome> biomeRegistry) {
            return getBiome(z*16 + x, biomeRegistry);
        }

        public static Biomes of(NBTIntArray a) {
            if(a == null)
                return null;
            switch (a.size()) {
                case 256:
                    return new Biomes() {
                        @Override
                        public int getBiome(int xz) {
                            return a.getInt(xz);
                        }

                        @Override
                        public IntStream stream() {
                            return a.stream();
                        }
                    };
                case 1024:
                    // simple implementation to get it 1.15 compatible
                    return new Biomes() {
                        @Override
                        public int getBiome(int xz) {
                            int x = xz & 15;
                            int z = xz >> 4;
                            int y = 64;
                            return a.getInt((x >> 2) | ((z >> 2) << 2) | ((y >> 2) << 4));
                        }

                        @Override
                        public IntStream stream() {
                            return IntStream.range(0, 256).map(this::getBiome);
                        }
                    };
                default:
                    return null;
            }
        }

        public static Biomes of(NBTByteArray a) {
            if(a == null || a.size() != 256)
                return null;
            return new Biomes() {
                @Override
                public int getBiome(int xz) {
                    return a.getUnsigned(xz);
                }

                @Override
                public IntStream stream() {
                    return a.stream();
                }
            };
        }
    }
}
