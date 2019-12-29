package mcworldinspector;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
    
    private final NBTTagCompound level;
    private final NBTLongArray heightmap;
    private final SubChunk[] subchunks = new SubChunk[16];

    public Chunk(int globalX, int globalZ, NBTTagCompound nbt) {
        super(globalX, globalZ);
        this.level = nbt.getCompound("Level");
        this.heightmap = level.getCompound("Heightmaps").get("MOTION_BLOCKING_NO_LEAVES", NBTLongArray.class);
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
        return heightmap == null;
    }

    public SubChunk getSubChunk(int y) {
        return subchunks[y];
    }

    public NBTIntArray getBiomes() {
        return level.get("Biomes", NBTIntArray.class);
    }
    
    public Biome getBiome(int x, int z, Map<Integer, Biome> biomeRegistry) {
        final NBTIntArray biomes = getBiomes();
        if(biomes != null && biomes.size() == 256)
            return biomeRegistry.get(biomes.getInt(z*16 + x));
        return null;
    }
    
    public NBTTagCompound getTopBlock(int x, int z) {
        if(heightmap != null) {
            int top = heightmap.getBits((z*16+x)*9, 9) - 1;
            if(top >= 0 && top < 256 && subchunks[top >> 4] != null)
                return subchunks[top >> 4].getBlock(x, top & 15, z);
        }
        return null;
    }

    public boolean isAir(int x, int y, int z) {
        final SubChunk sc = subchunks[y >> 4];
        return (sc == null) || sc.isAir(x, y & 15, z);
    }

    public NBTTagCompound getTopBlockBelowLayer(int x, int y, int z) {
        for(;;) {
            final SubChunk sc = subchunks[y >> 4];
            if(sc != null) {
                final NBTTagCompound block = sc.getTopBlockBelowLayer(x, y & 15, z);
                if(block != null)
                    return block;
            }
            if(y <= 15)
                return null;
            y = (y & ~15) - 16;
        }
    }

    public Stream<SubChunk.BlockInfo> findBlocks(String blockType) {
        return subChunks().flatMap(sc -> sc.findBlocks(blockType,
                new BlockPos(x << 4, sc.getGlobalY(), z << 4)));
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
    
    public Stream<NBTTagCompound> entities() {
        return level.getList("Entities", NBTTagCompound.class).stream();
    }

    public Stream<String> entityTypes() {
        return entities().flatMap(toID());
    }

    public Stream<MCColor> sheepColors() {
        return entities()
                .filter(v -> "minecraft:sheep".equals(v.getString("id")))
                .flatMap(v -> MCColor.asStream(v.get("Color", Byte.class)));
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
    
    public Stream<Biome> biomes(Map<Integer, Biome> biomeRegistry) {
        final NBTIntArray biomes = getBiomes();
        if(biomes == null)
            return Stream.empty();
        return biomes.stream().mapToObj(biomeRegistry::get).filter(Objects::nonNull);
    }

    private static Function<NBTTagCompound, Stream<String>> toID() {
        return e -> e.getStringAsStream("id");
    }

    private static Predicate<NBTTagCompound> filterByID(String id) {
        return v -> id.equals(v.getString("id"));
    }
}
