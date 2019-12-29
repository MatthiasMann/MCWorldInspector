package mcworldinspector;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.AsyncTask;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileHelpers;
import mcworldinspector.utils.FileOffsetError;

/**
 *
 * @author matthias
 */
public class World {
    
    private NBTTagCompound level = NBTTagCompound.EMPTY;
    private Map<Integer, Biome> biomeRegistry = Collections.EMPTY_MAP;
    private final HashMap<XZPosition, Chunk> chunks = new HashMap<>();
    private final TreeSet<String> blockTypes = new TreeSet<>();
    private final TreeSet<String> entityTypes = new TreeSet<>();
    private final TreeSet<MCColor> sheepColors = new TreeSet<>();
    private final TreeSet<String> tileEntityTypes = new TreeSet<>();
    private final TreeSet<String> structureTypes = new TreeSet<>();
    private Set<Biome> biomes = Collections.EMPTY_SET;

    private World() {
    }

    private void addChunk(Chunk chunk) {
        if(chunks.putIfAbsent(chunk, chunk) == null) {
            chunk.getBlockTypes().forEach(blockTypes::add);
            chunk.entityTypes().forEach(entityTypes::add);
            chunk.sheepColors().forEach(sheepColors::add);
            chunk.tileEntityTypes().forEach(tileEntityTypes::add);
            chunk.structureTypes().forEach(structureTypes::add);
        }
    }
    
    private void finish() {
        blockTypes.remove("minecraft:air");
        blockTypes.remove("minecraft:cave_air");
        blockTypes.remove("minecraft:bedrock");
        
        biomeRegistry = level.getCompound("fml").getCompound("Registries")
                .getCompound("minecraft:biome")
                .getList("ids", NBTTagCompound.class)
                .stream().flatMap(biome -> {
                    Integer value = biome.get("V", Integer.class);
                    String name = biome.getString("K");
                    if(value != null && name != null)
                        return Stream.of(new Biome(name, value));
                    return Stream.empty();
                }).collect(Collectors.toMap(Biome::getNumericID, v -> v));
        if(biomeRegistry.isEmpty())
            biomeRegistry = Biome.VANILLA_BIOMES;

        biomes = chunks.values().parallelStream()
                .flatMap(c -> c.biomes(biomeRegistry))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public NBTTagCompound getLevel() {
        return level;
    }

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    public Chunk getChunk(int x, int y) {
        return chunks.get(new XZPosition(x, y));
    }
    
    public Chunk getChunk(NBTDoubleArray pos) {
        return getChunk((int)pos.getDouble(0) >> 4, (int)pos.getDouble(2) >> 4);
    }
    
    public TreeSet<String> getBlockTypes() {
        return blockTypes;
    }

    public TreeSet<String> getEntityTypes() {
        return entityTypes;
    }

    public TreeSet<MCColor> getSheepColors() {
        return sheepColors;
    }

    public TreeSet<String> getTileEntityTypes() {
        return tileEntityTypes;
    }

    public TreeSet<String> getStructureTypes() {
        return structureTypes;
    }

    public Map<Integer, Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    public Set<Biome> getBiomes() {
        return biomes;
    }

    public Stream<Chunk> chunks() {
        return chunks.values().stream();
    }

    public long getRandomSeed() {
        final Long seed = level.getCompound("Data").get("RandomSeed", Long.class);
        return seed != null ? seed : 0;
    }

    public NBTDoubleArray getPlayerPos() {
        return level.getCompound("Data")
                    .getCompound("Player").get("Pos", NBTDoubleArray.class);
    }

    public Chunk getPlayerChunk() {
        NBTDoubleArray pos = getPlayerPos();
        return (pos != null) ? getChunk(pos) : null;
    }

    public Chunk getSpawnChunk() {
        NBTTagCompound data = level.getCompound("Data");
        Integer spawnX = data.get("SpawnX", Integer.class);
        Integer spawnZ = data.get("SpawnZ", Integer.class);
        return (spawnX != null && spawnZ != null) ?
                getChunk(spawnX >> 4, spawnZ >> 4) : null;
    }

    public static class AsyncLoading {
        private final World world = new World();
        private final ArrayList<FileError> errors = new ArrayList<>();
        private final AtomicInteger openFiles = new AtomicInteger();
        private final ExecutorService executor = Executors.newWorkStealingPool();
        private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private final BiConsumer<World, ArrayList<FileError>> done;
        private int progress = 0;
        private int total = 0;
        private Iterator<File> files;
        private String levelName = "Unknown";

        public AsyncLoading(BiConsumer<World, ArrayList<FileError>> done) {
            this.done = done;
        }

        public boolean start(File folder) {
            final File[] fileList = folder.listFiles((dir, name) -> name.endsWith(".mca"));
            if(fileList == null || fileList.length == 0)
                return false;

            assert(total == 0);
            total = fileList.length;
            files = Arrays.asList(fileList).iterator();

            File levelDatFile = FileHelpers.findFileThroughParents(folder, "level.dat", 2);
            if(levelDatFile != null) {
                ++total;
                AsyncTask.submit(executor, () -> loadLevelDat(levelDatFile),
                        result -> {
                            result.andThen(level -> {
                                world.level = level;
                                String oldName = levelName;
                                levelName = level.getCompound("Data").getString("LevelName");
                                propertyChangeSupport.firePropertyChange(
                                        "levelName", oldName, levelName);
                            }, ex -> errors.add(new FileError(levelDatFile, ex)));
                            incProgress();
                            checkDone();
                        });
            }

            submitAsyncLoads();
            return true;
        }

        public int getTotal() {
            return total;
        }

        public int getProgress() {
            return progress;
        }

        public String getLevelName() {
            return levelName;
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }

        private NBTTagCompound loadLevelDat(File file) throws Exception {
            return NBTTagCompound.parseGZip(FileHelpers.loadFile(file, 1<<20));
        }

        private void submitAsyncLoads() {
            int oldTotal = total;
            int oldProgress = progress;
            while(openFiles.get() < 10 && files.hasNext()) {
                final File file = files.next();
                try {
                    total += RegionFile.loadAsync(file, executor, openFiles,
                            (result, offset) -> {
                                result.andThen(chunk -> {
                                    if(!chunk.isEmpty())
                                        world.addChunk(chunk);
                                }, ex -> errors.add(
                                        new FileOffsetError(file, offset, ex)));
                                incProgress();
                                submitAsyncLoads();
                                checkDone();
                            });
                } catch(IOException ex) {
                    errors.add(new FileError(file, ex));
                }
                ++progress;
            }
            
            propertyChangeSupport.firePropertyChange("total", oldTotal, total);
            propertyChangeSupport.firePropertyChange("progress", oldProgress, progress);
        }
        
        private void incProgress() {
            int oldProgress = progress++;
            propertyChangeSupport.firePropertyChange("progress", oldProgress, progress);
        }
        
        private void checkDone() {
            assert(progress <= total);
            if(progress == total) {
                assert(!files.hasNext());
                executor.shutdown();
                world.finish();
                done.accept(world, errors);
            }
        }
    }
}
