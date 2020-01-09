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

    private final RandomAccessFile raf;
    private final long fileSize;
    private long fileUsed;

    private RegionFile(File file) throws IOException {
        this.raf = new RandomAccessFile(file, "r");
        this.fileSize = raf.length();
        this.fileUsed = 4096;   // size of the offset table
    }

    private void close() {
        try {
            raf.close();
        } catch(IOException ex) {}
    }

    private void read(ByteBuffer bb, long offset, String msg) throws IOException {
        try {
            raf.getChannel().read(bb, offset);
        } catch(IOException ex) {
            throw new IOException(msg, ex);
        }
        if(bb.hasRemaining())
            throw new EOFException(msg);
        bb.flip();
    }

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

    private static final ThreadLocalOffsets OFFSETS = new ThreadLocalOffsets();
    private static final ThreadLocalChunkBuffers BUFFERS = new ThreadLocalChunkBuffers();

    @SuppressWarnings("UseSpecificCatch")
    public static int loadAsync(File file, ExecutorService e, AtomicInteger openFiles, LoadCompleted c) throws IOException {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        if(!matcher.matches())
            throw new IOException("Invalid file name: " + file);
        
        int globalX = Integer.parseInt(matcher.group(1)) * 32;
        int globalZ = Integer.parseInt(matcher.group(2)) * 32;
        ByteBuffer offsets = OFFSETS.get();

        final RegionFile rf = new RegionFile(file);
        try {
            rf.read(offsets, 0, "Could not read chunk offsets");
        } catch(IOException ex) {
            rf.close();
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
                            return rf.loadChunk(offset, chunkX, chunkZ);
                        } catch(IOException ex) {
                            throw new IOExceptionWithOffset(offset, ex);
                        }
                    };
                }), results -> {
                    openFiles.decrementAndGet();
                    rf.close();
                    c.loadCompleted(results, rf.fileSize, rf.fileUsed);
                });
    }

    private Chunk loadChunk(int offset, int globalX, int globalZ) throws IOException {
        final var buffers = BUFFERS.get();
        final var file_offset = (long)(offset >> 8) * 4096L;
        read(buffers.header, file_offset, "Could not read chunk header");
        final var size = buffers.header.getInt(0) - 1;
        final var type = buffers.header.get(4);
        final var compressed = buffers.getCompressed(size);
        read(compressed, file_offset + 5, "Could not read compressed chunk");
        fileUsed += (size + 5 + 4095) & - 4096;

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

    public @FunctionalInterface interface LoadCompleted {
        public void loadCompleted(List<Expected<Chunk>> chunks, long fileSize, long used);
    }

    private static class ThreadLocalOffsets extends ThreadLocal<ByteBuffer> {
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
    }

    private static class ThreadLocalChunkBuffers extends ThreadLocal<ChunkBuffers> {
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
    }
}
