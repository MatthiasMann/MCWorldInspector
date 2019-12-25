package mcworldinspector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                subchunks[y] = new SubChunk(palette, blockStates, (byte)y);
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
    
    public Stream<String> entities() {
        return level.getList("Entities", NBTTagCompound.class)
                .stream().map(e -> e.getString("id")).filter(Objects::nonNull);
    }

    public Stream<NBTTagCompound> getEntities(String id) {
        return level.getList("Entities", NBTTagCompound.class).stream()
                .filter(v -> id.equals(v.getString("id")));
    }
    
    public Stream<String> structureTypes() {
        return level.getCompound("Structures").getCompound("Starts")
                .values(NBTTagCompound.class).map(v -> v.getString("id"))
                .filter(id -> id != null && !"INVALID".equals(id));
    }
    
    public Stream<NBTTagCompound> getStructures(String id) {
        return level.getCompound("Structures").getCompound("Starts")
                .values(NBTTagCompound.class)
                .filter(v -> id.equals(v.getString("id")));
    }
    
    public Stream<Biome> biomes() {
        final NBTIntArray biomes = level.get("Biomes", NBTIntArray.class);
        if(biomes == null)
            return Stream.empty();
        return biomes.stream().mapToObj(BIOMES::get).filter(Objects::nonNull);
    }
    
    public static final class Biome implements Comparable<Biome> {
        public final String name;
        public final String namespaced_id;
        public final int numeric_id;

        public Biome(String name, String namespaced_id, int numeric_id) {
            this.name = name;
            this.namespaced_id = namespaced_id;
            this.numeric_id = numeric_id;
        }

        @Override
        public int hashCode() {
            return numeric_id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Biome) {
                return ((Biome)obj).numeric_id == this.numeric_id;
            }
            return false;
        }

        @Override
        public int compareTo(Biome o) {
            return name.compareTo(o.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    private final static HashMap<Integer, Biome> BIOMES = new HashMap<>();

    static {
        Pattern pattern = Pattern.compile("^([^\\t]+)\\t([^\\t]+)\\t(\\d+)$");
        try(InputStream is = Chunk.class.getResourceAsStream("biomes.txt");
                InputStreamReader isr = new InputStreamReader(is, "UTF8");
                BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(line -> {
                final Matcher m = pattern.matcher(line);
                if(m.matches()) {
                    Biome biome = new Biome(m.group(1), m.group(2), Integer.parseInt(m.group(3)));
                    BIOMES.put(biome.numeric_id, biome);
                }
            });
        } catch(Exception ex) {
        }
    }
}
