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
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.Expected;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileHelpers;
import mcworldinspector.utils.IOExceptionWithFile;

/**
 *
 * @author matthias
 */
public class World {
    
    private NBTTagCompound level = NBTTagCompound.EMPTY;
    private Map<Integer, Biome> biomeRegistry = Collections.emptyMap();
    private final HashMap<XZPosition, Chunk> chunks = new HashMap<>();
    private final TreeMap<Integer, MCMap> maps = new TreeMap<>();
    private File folder;
    private int regionFilesCount;
    private long regionFilesTotalSize;
    private long regionFilesUsed;
    private SubChunk12.GlobalMapping globalMapping12;

    private World() {
    }

    private void finish() {
        biomeRegistry = getRegistry("minecraft:biome", "minecraft:biomes", "minecraft:worldgen/biome")
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

        final var dataVersion = level.getCompound("Data")
                .get("DataVersion", Integer.class, 0);
        if(dataVersion <= 1343) {
            final var gm = new SubChunk12.GlobalMapping(level, folder);
            globalMapping12 = gm;
            chunks.values().parallelStream()
                    .flatMap(Chunk::subChunks)
                    .forEach(sc -> {
                        if(sc instanceof SubChunk12)
                            ((SubChunk12)sc).setGlobalMapping(gm);
                    });
        }
    }

    public SubChunk12.GlobalMapping getGlobalMapping12() {
        return globalMapping12;
    }

    public int getRegionFilesCount() {
        return regionFilesCount;
    }

    public long getRegionFilesTotalSize() {
        return regionFilesTotalSize;
    }

    public long getRegionFilesUsed() {
        return regionFilesUsed;
    }

    public NBTTagCompound getLevel() {
        return level;
    }

    public NBTTagCompound getFML() {
        final var fml = level.getCompound("fml");
        return fml.isEmpty() ? level.getCompound("FML") : fml;
    }

    public NBTTagCompound getRegistry(String... names) {
        final var registries = getFML().getCompound("Registries");
        return Stream.of(names).map(registries::getCompound).filter(nbt -> !nbt.isEmpty()).findFirst().orElse(NBTTagCompound.EMPTY);
    }

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    public TreeMap<Integer, MCMap> getMaps() {
        return maps;
    }

    public Chunk getChunk(int x, int y) {
        return chunks.get(new XZPosition(x, y));
    }
    
    public Chunk getChunk(NBTDoubleArray pos) {
        return getChunk((int)pos.getDouble(0) >> 4, (int)pos.getDouble(2) >> 4);
    }

    public Map<Integer, Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    public Stream<Chunk> chunks() {
        return chunks.values().stream();
    }

    public Stream<Chunk> chunks(int x0, int z0, int x1, int z1) {
        if(x1 < x0 || z1 < z0)
            return Stream.empty();
        final var width = (x1 - x0) + 1;
        final var height = (z1 - z0) + 1;
        return IntStream.range(0, width * height)
                .mapToObj(idx -> getChunk(x0 + idx % width, z0 + idx / width))
                .filter(Objects::nonNull);
    }

    public Stream<Chunk> chunks(NBTIntArray bb) {
        if(bb == null || bb.size() != 6)
            return Stream.empty();
        return chunks(
                bb.getInt(0) >> 4, bb.getInt(2) >> 4,
                bb.getInt(3) >> 4, bb.getInt(5) >> 4);
    }

    public String getName() {
         return level.getCompound("Data").get("LevelName", String.class, "Unknown");
    }

    public long getRandomSeed() {
        final Long seed = level.getCompound("Data").get("RandomSeed", Long.class);
        return seed != null ? seed : 0;
    }

    public NBTTagCompound getPlayerData() {
        return level.getCompound("Data").getCompound("Player");
    }

    public NBTDoubleArray getPlayerPos() {
        return getPlayerData().get("Pos", NBTDoubleArray.class);
    }

    public Chunk getPlayerChunk() {
        NBTDoubleArray pos = getPlayerPos();
        return (pos != null) ? getChunk(pos) : null;
    }

    public XZPosition getSpawnPos() {
        NBTTagCompound data = level.getCompound("Data");
        Integer spawnX = data.get("SpawnX", Integer.class);
        Integer spawnZ = data.get("SpawnZ", Integer.class);
        return (spawnX != null && spawnZ != null) ?
                new XZPosition(spawnX, spawnZ) : null;
    }

    public Chunk getSpawnChunk() {
        NBTTagCompound data = level.getCompound("Data");
        Integer spawnX = data.get("SpawnX", Integer.class);
        Integer spawnZ = data.get("SpawnZ", Integer.class);
        return (spawnX != null && spawnZ != null) ?
                getChunk(spawnX >> 4, spawnZ >> 4) : null;
    }

    public void loadMapMarkers(NBTTagCompound mapNbt) {
        Integer mapId = mapNbt.get("map", Integer.class);
        if(mapId == null)
            return;
        final var map = maps.get(mapId);
        map.setDecorations(mapNbt.getList("Decorations", NBTTagCompound.class));
    }

    public static class AsyncLoading {
        private static final AtomicInteger worldNumber = new AtomicInteger(1);
        private final World world = new World();
        private final ArrayList<FileError> errors = new ArrayList<>();
        private final AtomicInteger openFiles = new AtomicInteger();
        private final ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            private final String prefix = "World " + worldNumber.getAndIncrement() + " loading thread ";
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, prefix + threadNumber.getAndIncrement());
            }
        });
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
            world.folder = FileHelpers.findFileThroughParents(folder, "options.txt", 3).getParentFile();

            File levelDatFile = FileHelpers.findFileThroughParents(folder, "level.dat", 2);
            if(levelDatFile != null) {
                ++total;
                AsyncExecution.submit(executor, () -> loadLevelDat(levelDatFile),
                        result -> {
                            result.andThen(level -> {
                                world.level = level;
                                String oldName = levelName;
                                levelName = world.getName();
                                propertyChangeSupport.firePropertyChange(
                                        "levelName", oldName, levelName);
                            }, ex -> errors.add(new FileError(levelDatFile, ex)));
                            incProgress(1);
                            checkDone();
                        });
                File[] maps = new File(levelDatFile.getParentFile(), "data")
                        .listFiles((dir, fileName) -> fileName.startsWith("map_")
                                && fileName.endsWith(".dat"));
                total += AsyncExecution.<MCMap>submit(executor, Arrays.stream(maps)
                        .map(file -> () -> {
                            try {
                                return MCMap.loadMap(file);
                            } catch(IOException ex) {
                                throw new IOExceptionWithFile(file, ex);
                            }
                        }), results -> {
                            results.forEach(Expected.consumer(map -> {
                                if(map != null && map.getIndex() >= 0)
                                    world.maps.put(map.getIndex(), map);
                            }, e -> {
                                if(e instanceof IOExceptionWithFile) {
                                    IOExceptionWithFile ex = (IOExceptionWithFile) e;
                                    errors.add(new FileError(ex.getFile(), ex.getCause()));
                                }
                            }));
                            incProgress(results.size());
                            checkDone();
                        });
            }

            propertyChangeSupport.firePropertyChange("total", 0, total);
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
                final HashMap<XZPosition, Chunk> chunks = world.chunks;
                final Consumer<Expected<Chunk>> handler = Expected.consumer(
                        chunk -> {
                            if(!chunk.isEmpty())
                                chunks.put(chunk, chunk);
                        }, errors, file);
                try {
                    total += RegionFile.loadAsync(file, executor, openFiles,
                            (results, fileSize, used) -> {
                                world.regionFilesCount++;
                                world.regionFilesTotalSize += fileSize;
                                world.regionFilesUsed += used;
                                results.forEach(handler);
                                incProgress(results.size());
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
        
        private void incProgress(int amount) {
            int oldProgress = progress;
            progress += amount;
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
