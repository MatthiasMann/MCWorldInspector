package mcworldinspector;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.Expected;

/**
 *
 * @author matthias
 */
public class RegionFile {
    
    private static final Pattern NAME_PATTERN = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private RegionFile() {}

    @SuppressWarnings("UseSpecificCatch")
    public static int loadAsync(File file, ExecutorService e, AtomicInteger openFiles, Consumer<Expected<Chunk>> c) throws IOException {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        if(!matcher.matches())
            throw new IOException("Invalid file name: " + file);
        
        int globalX = Integer.parseInt(matcher.group(1)) * 32;
        int globalZ = Integer.parseInt(matcher.group(2)) * 32;
        AtomicInteger outstanding = new AtomicInteger();
        ByteBuffer offsets = ByteBuffer.allocate(4096);

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            if(raf.getChannel().read(offsets, 0) != offsets.capacity())
                throw new EOFException();
            offsets.flip();
        } catch(IOException ex) {
            raf.close();
        }

        openFiles.incrementAndGet();
        int submittedChunks = 0;
        for(int z=0 ; z<32 ; z++) {
            final int chunkZ = globalZ + z;
            for(int x=0 ; x<32 ; x++) {
                final int offset = offsets.getInt();
                if(offset > 0) {
                    final int chunkX = globalX + x;
                    ++submittedChunks;
                    outstanding.incrementAndGet();
                    e.submit(() -> {
                        Expected<Chunk> v;
                        try {
                            v = new Expected<>(loadChunk(raf, offset, chunkX, chunkZ));
                        } catch(Exception ex) {
                            v = new Expected<>(ex);
                        } finally {
                            if(outstanding.decrementAndGet() == 0)
                                closeFile(raf, openFiles);
                        }
                        c.accept(v);
                    });
                }
            }
        }
        
        if(submittedChunks == 0)
            closeFile(raf, openFiles);
        return submittedChunks;
    }
    
    private static void closeFile(RandomAccessFile raf, AtomicInteger openFiles) {
        openFiles.decrementAndGet();
        try {
            raf.close();
        } catch(IOException ex) {}
    }

    private static Chunk loadChunk(RandomAccessFile raf, int offset, int global_x, int global_z) throws IOException {
        long file_offset = (long)(offset >> 8) * 4096L;
        ByteBuffer header = ByteBuffer.allocate(5);
        raf.getChannel().read(header, file_offset);
        int size = header.getInt(0);
        int type = header.get(4);
        ByteBuffer chunk_gz = ByteBuffer.allocate(size - 1);
        raf.getChannel().read(chunk_gz, file_offset + 5);

        ByteBuffer chunk = ByteBuffer.allocate(1 << 20);
        Inflater i = new Inflater();
        i.setInput(chunk_gz.array(), 0, chunk_gz.position());
        try {
            int len = i.inflate(chunk.array());
            chunk = ByteBuffer.wrap(Arrays.copyOf(chunk.array(), len));
        } catch(DataFormatException e) {
            throw new IOException(e);
        }

        try {
            NBTTagCompound nbt = NBTTagCompound.parse(chunk);
            return new Chunk(global_x, global_z, nbt);
        } catch(java.nio.BufferUnderflowException e) {
            throw new IOException("NBT data corrupted");
        }
    }
}
