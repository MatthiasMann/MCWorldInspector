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
import mcworldinspector.utils.FileHelpers;
import mcworldinspector.utils.IOExceptionWithOffset;

/**
 *
 * @author matthias
 */
public class RegionFile {
    
    private static final Pattern NAME_PATTERN = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private RegionFile() {}

    private static class ChunkBuffers {
        final ByteBuffer header = ByteBuffer.allocateDirect(5);
        final ByteBuffer uncompressed = ByteBuffer.allocateDirect(1 << 20);
        private ByteBuffer compressed;

        public ByteBuffer getCompressed(int size) {
            if(compressed == null || compressed.capacity() < size)
                compressed = ByteBuffer.allocateDirect((size + 4095) & -4096);
            compressed.clear().limit(size);
            return compressed;
        }
    }

    private static final ThreadLocal<ByteBuffer> OFFSETS = new ThreadLocal<>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocateDirect(4096);
        }

        @Override
        public ByteBuffer get() {
            final ByteBuffer b = super.get();
            b.clear();
            return b;
        }

    };
    private static final ThreadLocal<ChunkBuffers> BUFFERS = new ThreadLocal<>() {
        @Override
        protected ChunkBuffers initialValue() {
            return new ChunkBuffers();
        }

        @Override
        public ChunkBuffers get() {
            final ChunkBuffers buffers = super.get();
            buffers.header.clear();
            buffers.uncompressed.clear();
            return buffers;
        }
    };

    @SuppressWarnings("UseSpecificCatch")
    public static int loadAsync(File file, ExecutorService e, AtomicInteger openFiles, Consumer<List<Expected<Chunk>>> c) throws IOException {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        if(!matcher.matches())
            throw new IOException("Invalid file name: " + file);
        
        int globalX = Integer.parseInt(matcher.group(1)) * 32;
        int globalZ = Integer.parseInt(matcher.group(2)) * 32;
        ByteBuffer offsets = OFFSETS.get();

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            offsets.clear();
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
        final ChunkBuffers buffers = BUFFERS.get();
        final long file_offset = (long)(offset >> 8) * 4096L;
        if(raf.getChannel().read(buffers.header, file_offset) != 5)
            throw new EOFException("Could not read chunk header");
        final int size = buffers.header.getInt(0) - 1;
        final int type = buffers.header.get(4);
        final ByteBuffer compressed = buffers.getCompressed(size);
        if(raf.getChannel().read(compressed, file_offset + 5) != size)
            throw new EOFException("Could not read compressed chunk");
        compressed.flip();

        try {
            final NBTTagCompound nbt;
            switch (type) {
                case 1:
                    FileHelpers.parseGZipHeader(compressed);
                    nbt = NBTTagCompound.parseInflate(compressed, buffers.uncompressed, true);
                    break;
                case 2:
                    nbt = NBTTagCompound.parseInflate(compressed, buffers.uncompressed, false);
                    break;
                default:
                    throw new IOException("Unsupported chunk compression type: " + type);
            }
            return new Chunk(globalX, globalZ, nbt);
        } catch(DataFormatException e) {
            throw new IOException(e);
        } catch(java.nio.BufferUnderflowException e) {
            throw new IOException("NBT data corrupted");
        }
    }
}
