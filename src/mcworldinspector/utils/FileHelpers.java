package mcworldinspector.utils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 *
 * @author matthias
 */
public final class FileHelpers {
    
    private static final int GZIP_MAGIC1 = 0x8b;
    private static final int GZIP_MAGIC0 = 0x1f;

    private FileHelpers() {
    }

    public static File findFileThroughParents(File folder, String name, int maxLevels) {
        while(folder != null && maxLevels-- >= 0) {
            File file = new File(folder, name);
            if(file.exists())
                return file;
            folder = folder.getParentFile();
        }
        return null;
    }

    public static ByteBuffer loadFile(File file, int maxSize) throws IOException {
        try(RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final long length = raf.length();
            if(length > maxSize)
                throw new IOException("File size of " + length +
                        " exceeds maximum allowed size of " + maxSize + " bytes");
            ByteBuffer buffer = ByteBuffer.allocate((int)length + 1);
            final int read = raf.getChannel().read(buffer);
            if(read != length)
                throw new EOFException("Could only read " +read + " of " + length + " bytes");
            buffer.flip();
            return buffer;
        }
    }
    
    public static boolean isGZip(ByteBuffer data) {
        return data.remaining() > 10 &&
                (data.get(0) & 255) == GZIP_MAGIC0 &&
                (data.get(1) & 255) == GZIP_MAGIC1;
    }
    
    public static void parseGZipHeader(ByteBuffer compressed) throws IOException {
        if((compressed.get() & 255) != GZIP_MAGIC0)
            throw new IOException("Error in GZIP header, bad magic code");
        if((compressed.get() & 255) != GZIP_MAGIC1)
            throw new IOException("Error in GZIP header, bad magic code");
        if((compressed.get() & 255) != Deflater.DEFLATED)
            throw new IOException("Error in GZIP header, data not in deflate format");
        CRC32 headCRC = new CRC32();
        headCRC.update(GZIP_MAGIC0);
        headCRC.update(GZIP_MAGIC1);
        headCRC.update(Deflater.DEFLATED);
        final int flags = compressed.get() & 255;
        headCRC.update(flags);   
        if ((flags & 0xd0) != 0)
           throw new IOException("Reserved flag bits in GZIP header != 0");
        // skip the modification time, extra flags, and OS type
        for (int i=0; i< 6; i++)
            headCRC.update(compressed.get() & 255);
        // read extra field
        if ((flags & 0x04) != 0) {
            /* Skip subfield id */
            for (int i = 0; i < 2; i++)
                headCRC.update(compressed.get() & 255);
            final int len1 = compressed.get() & 255;
            final int len2 = compressed.get() & 255;
            headCRC.update(len1);
            headCRC.update(len2);
            final int len = (len1 << 8) | len2;
            for (int i = 0; i < len; i++)
                headCRC.update(compressed.get() & 255);
        }
        // read file name
        if ((flags & 0x08) != 0) {
            int c;
            do {
                c = compressed.get() & 255;
                headCRC.update(c);
            } while(c > 0);
        }
        // read comment
        if ((flags & 0x10) != 0) {
            int c;
            do {
                c = compressed.get() & 255;
                headCRC.update(c);
            } while(c > 0);
        }
        // read header CRC
        if ((flags & 0x02) != 0) {
            final int crc0 = compressed.get() & 255;
            final int crc1 = compressed.get() & 255;
            final int crc = (crc0 << 8) | crc1;
            if (crc != ((int)headCRC.getValue() & 0xffff))
                throw new IOException("Header CRC value mismatch");
        }
    }
}
