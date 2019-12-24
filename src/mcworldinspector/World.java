package mcworldinspector;

import java.awt.EventQueue;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.Expected;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileOffsetError;

/**
 *
 * @author matthias
 */
public class World {
    
    private final NBTTagCompound level;
    private final HashSet<Chunk> chunks;
    private final TreeSet<String> blockTypes;
    private final TreeSet<String> entityTypes;

    public World(NBTTagCompound level, HashSet<Chunk> chunks, TreeSet<String> blockTypes, TreeSet<String> entityTypes) {
        this.level = level;
        this.chunks = chunks;
        this.blockTypes = blockTypes;
        this.entityTypes = entityTypes;
    }

    public NBTTagCompound getLevel() {
        return level;
    }

    public HashSet<Chunk> getChunks() {
        return chunks;
    }

    public TreeSet<String> getBlockTypes() {
        return blockTypes;
    }

    public TreeSet<String> getEntityTypes() {
        return entityTypes;
    }

    public Stream<Chunk> chunks() {
        return chunks.stream();
    }

    public long getRandomSeed() {
        final Long seed = level.get("RandomSeed", Long.class);
        return seed != null ? seed : 0;
    }

    public Stream<Chunk> slimeChunks() {
        long seed = getRandomSeed();
        return chunks.stream().filter(c -> c.isSlimeChunk(seed));
    }

    public static class AsyncLoading {
        private final File folder;
        private final HashSet<Chunk> chunks = new HashSet<>();
        private final ArrayList<FileError> errors = new ArrayList<>();
        private final TreeSet<String> blockTypes = new TreeSet<>();
        private final TreeSet<String> entityTypes = new TreeSet<>();
        private final AtomicInteger openFiles = new AtomicInteger();
        private final ExecutorService executor = Executors.newWorkStealingPool();
        private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private final BiConsumer<World, ArrayList<FileError>> done;
        private int progress = 0;
        private int total = 0;
        private Iterator<File> files;
        private NBTTagCompound level = NBTTagCompound.EMPTY;
        private String levelName = "Unknown";

        public AsyncLoading(File folder, BiConsumer<World, ArrayList<FileError>> done) {
            this.folder = folder;
            this.done = done;
        }

        public boolean start() {
            final File[] fileList = folder.listFiles((dir, name) -> name.endsWith(".mca"));
            if(fileList == null || fileList.length == 0)
                return false;
            assert(total == 0);
            total = fileList.length;
            files = Arrays.asList(fileList).iterator();
            submitAsyncLoads();
            
            File path = folder;
            while(path != null) {
                File file = new File(path, "level.dat"); 
                if(file.exists()) {
                    ++total;
                    executor.submit(() -> {
                        final Expected<NBTTagCompound> nbt =
                                Expected.wrap(() -> loadLevelDat(file));
                        EventQueue.invokeLater(() -> processLevelDat(nbt, file));
                    });
                    break;
                }
                path = path.getParentFile();
            }
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
            try(RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long length = raf.length();
                if(length > (1<<20))
                    throw new IOException("level.dat too large");
                ByteBuffer b = ByteBuffer.allocate((int)length + 1);
                if(raf.getChannel().read(b) != length)
                    throw new EOFException("Unable to read level.dat");
                b.put((byte)0); // dummy byte for Inflate
                b.flip();
                return NBTTagCompound.parseGZip(b).getCompound("Data");
            }
        }
        
        private void processLevelDat(Expected<NBTTagCompound> v, File file) {
             try {
                level = v.get();
                String name = level.getString("LevelName");
                if(name != null && !levelName.equals(name)) {
                    String oldName = levelName;
                    levelName = name;
                    propertyChangeSupport.firePropertyChange("levelName", oldName, name);
                }
            } catch (Exception ex) {
                errors.add(new FileError(file, ex));
            }
            incProgress();
            checkDone();
        }

        private void submitAsyncLoads() {
            int oldTotal = total;
            int oldProgress = progress;
            while(openFiles.get() < 10 && files.hasNext()) {
                final File file = files.next();
                try {
                    total += RegionFile.loadAsync(file, executor, openFiles,
                            (chunk, offset) -> EventQueue.invokeLater(
                                    () -> processResult(chunk, file, offset)));
                } catch(IOException ex) {
                    errors.add(new FileError(file, ex));
                }
                ++progress;
            }
            
            if(oldTotal != total)
                propertyChangeSupport.firePropertyChange("total", oldTotal, total);
            if(oldProgress != progress)
                propertyChangeSupport.firePropertyChange("progress", oldProgress, progress);
        }

        private void processResult(Expected<Chunk> v, File file, int offset) {
            try {
                Chunk chunk = v.get();
                if(!chunk.isEmpty()) {
                    chunks.add(chunk);
                    chunk.getBlockTypes().forEach(blockTypes::add);
                    chunk.entities().forEach(entityTypes::add);
                }
            } catch(Exception e) {
                errors.add(new FileOffsetError(file, offset, e));
            }
            incProgress();
            submitAsyncLoads();
            checkDone();
        }
        
        private void incProgress() {
            int oldProgress = progress++;
            propertyChangeSupport.firePropertyChange("progress", oldProgress, progress);
        }
        
        private void checkDone() {
            if(progress == total) {
                assert(!files.hasNext());
                executor.shutdown();
                blockTypes.remove("minecraft:air");
                blockTypes.remove("minecraft:cave_air");
                blockTypes.remove("minecraft:bedrock");
                done.accept(new World(level, chunks, blockTypes, entityTypes), errors);
            }
        }
    }
}
