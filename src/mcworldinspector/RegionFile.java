package mcworldinspector;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.zip.DataFormatException;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.Expected;
import mcworldinspector.utils.IOExceptionWithOffset;

/**
 *
 * @author matthias
 */
public class RegionFile {
    
    private static final Pattern NAME_PATTERN = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private RegionFile() {}

    @SuppressWarnings("UseSpecificCatch")
    public static int loadAsync(File file, ExecutorService e, AtomicInteger openFiles, Consumer<List<Expected<Chunk>>> c) throws IOException {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        if(!matcher.matches())
            throw new IOException("Invalid file name: " + file);
        
        int globalX = Integer.parseInt(matcher.group(1)) * 32;
        int globalZ = Integer.parseInt(matcher.group(2)) * 32;
        ByteBuffer offsets = ByteBuffer.allocate(4096);

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            if(raf.getChannel().read(offsets, 0) != offsets.capacity())
                throw new EOFException();
            offsets.flip();
        } catch(IOException ex) {
            raf.close();
            throw ex;
        }

        openFiles.incrementAndGet();
        return AsyncExecution.<Chunk>submit(e,
                IntStream.range(0, 32*32).mapToObj(idx -> {
                    final int offset = offsets.getInt(idx * 4);
                    if(offset <= 0)
                        return null;
                    final int chunkZ = globalZ + (idx >> 5);
                    final int chunkX = globalX + (idx & 31);
                    return () -> {
                        try {
                            return loadChunk(raf, offset, chunkX, chunkZ);
                        } catch(IOException ex) {
                            throw new IOExceptionWithOffset(offset, ex);
                        }
                    };
                }), results -> {
                    openFiles.decrementAndGet();
                    try {
                        raf.close();
                    } catch(IOException ex) {}
                    c.accept(results);
                });
    }

    private static Chunk loadChunk(RandomAccessFile raf, int offset, int globalX, int globalZ) throws IOException {
        long file_offset = (long)(offset >> 8) * 4096L;
        ByteBuffer header = ByteBuffer.allocate(5);
        raf.getChannel().read(header, file_offset);
        int size = header.getInt(0);
        int type = header.get(4);
        ByteBuffer chunk_gz = ByteBuffer.allocate(size - 1);
        if(raf.getChannel().read(chunk_gz, file_offset + 5) != chunk_gz.capacity())
            throw new EOFException("Could not read compressed chunk");
        chunk_gz.flip();

        try {
            final NBTTagCompound nbt;
            switch (type) {
                case 1: nbt = NBTTagCompound.parseGZip(chunk_gz); break;
                case 2: nbt = NBTTagCompound.parseInflate(chunk_gz); break;
                default: throw new IOException("Unsupported chunk compression type: " + type);
            }
            return new Chunk(globalX, globalZ, nbt);
        } catch(DataFormatException e) {
            throw new IOException(e);
        } catch(java.nio.BufferUnderflowException e) {
            throw new IOException("NBT data corrupted");
        }
    }
}
