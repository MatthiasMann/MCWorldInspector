package mcworldinspector.nbt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 *
 * @author matthias
 */
public class NBTTagCompound extends NBTBase implements Iterable<Map.Entry<String, Object>> {
    
    public static final NBTTagCompound EMPTY = new NBTTagCompound();

    final IdentityHashMap<String, Object> entries = new IdentityHashMap<>();

    private NBTTagCompound() {
    }

    public Object get(String name) {
        return entries.get(name);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return Collections.unmodifiableSet(entries.entrySet()).iterator();
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return Collections.unmodifiableSet(entries.entrySet());
    }

    public Stream<Object> values() {
        return entries.values().stream();
    }

    public<T> Stream<T> values(Class<T> type) {
        return values().filter(type::isInstance).map(type::cast);
    }

    public<U> U get(String name, Class<U> type) {
        Object o = entries.get(name);
        return type.isInstance(o) ? type.cast(o) : null;
    }

    public<U> U get(String name, Class<U> type, U defaultValue) {
        Object o = entries.get(name);
        return type.isInstance(o) ? type.cast(o) : defaultValue;
    }
    
    public IntStream getByteAsStream(String name) {
        Object o = entries.get(name);
        return (o instanceof Byte) ? IntStream.of((Byte)o) : IntStream.empty();
    }
    
    public IntStream getIntAsStream(String name) {
        Object o = entries.get(name);
        return (o instanceof Integer) ? IntStream.of((Integer)o) : IntStream.empty();
    }

    public String getString(String name) {
        Object o = entries.get(name);
        return (o instanceof String) ? (String)o : null;
    }

    public Stream<String> getStringAsStream(String name) {
        Object o = entries.get(name);
        return (o instanceof String) ? Stream.of((String)o) : Stream.empty();
    }

    public NBTTagCompound getCompound(String name) {
        Object o = entries.get(name);
        return (o instanceof NBTTagCompound) ? (NBTTagCompound)o : EMPTY;
    }

    public<U> NBTTagList<U> getList(String name, Class<U> type) {
        Object o = entries.get(name);
        return (o instanceof NBTTagList) ? ((NBTTagList)o).as(type) : NBTTagList.EMPTY;
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    public static NBTTagCompound parse(ByteBuffer data) {
        if(data.get() != 10)
            throw new IllegalArgumentException("Root tag must be an NBTTagCompound");
        if(data.getChar() != 0)
            throw new IllegalArgumentException("Root tag must not have a name");
        return (NBTTagCompound)parseNBTValue(data, 10);
    }

    public static NBTTagCompound parseInflate(ByteBuffer compressed, boolean unwarp) throws DataFormatException, IOException {
        byte[] tmp = new byte[1 << 20];
        Inflater i = new Inflater(unwarp);
        i.setInput(compressed.array(), compressed.position(), compressed.remaining());
        int len = i.inflate(tmp);
        if(!i.finished())
            throw new IOException("NBT data bigger than 1 MB");
        return parse(ByteBuffer.wrap(Arrays.copyOf(tmp, len)));
    }

    public static NBTTagCompound parseGZip(ByteBuffer compressed) throws IOException, DataFormatException {
        if((compressed.get() & 255) != 0x1f)
            throw new IOException("Error in GZIP header, bad magic code");
        if((compressed.get() & 255) != 0x8b)
            throw new IOException("Error in GZIP header, bad magic code");
        if((compressed.get() & 255) != Deflater.DEFLATED)
            throw new IOException("Error in GZIP header, data not in deflate format");
        CRC32 headCRC = new CRC32();
        headCRC.update(0x1f);
        headCRC.update(0x8b);
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
        return parseInflate(compressed, true);
    }

    private static Object parseNBTValue(ByteBuffer data, int tag) {
        switch(tag) {
            case 1: return data.get();
            case 2: return data.getShort();
            case 3: return data.getInt();
            case 4: return data.getLong();
            case 5: return data.getFloat();
            case 6: return data.getDouble();
            case 7: return new NBTByteArray(slice(data, data.getInt()));
            case 8: return readUTF8(data);
            case 9: {
                int tagid = data.get();
                int len = data.getInt();
                switch (tagid) {
                    case 0: return null;
                    case 1: return new NBTByteArray(slice(data, len));
                    case 2: return new NBTShortArray(slice(data, len*2).asShortBuffer());
                    case 3: return new NBTIntArray(slice(data, len*4).asIntBuffer());
                    case 4: return new NBTLongArray(slice(data, len*8).asLongBuffer());
                    case 5: return new NBTFloatArray(slice(data, len*4).asFloatBuffer());
                    case 6: return new NBTDoubleArray(slice(data, len*8).asDoubleBuffer());
                    case 7: return parseTagList(data, tagid, len, NBTByteArray.class);
                    case 8: return parseTagList(data, tagid, len, String.class);
                    case 9: return parseTagList(data, tagid, len, NBTArray.class);
                    case 10: return parseTagList(data, tagid, len, NBTTagCompound.class);
                    case 11: return parseTagList(data, tagid, len, NBTIntArray.class);
                    case 12: return parseTagList(data, tagid, len, NBTLongArray.class);
                    default:
                        throw new IllegalArgumentException("Unknown TAG=" + tagid);
                }
            }
            case 10: {
                NBTTagCompound map = new NBTTagCompound();
                int tagid;
                while((tagid=data.get()) != 0) {
                    String name = readUTF8(data).intern();
                    map.entries.put(name, parseNBTValue(data, tagid));
                }
                return map;
            }
            case 11: return new NBTIntArray(slice(data, data.getInt()*4).asIntBuffer());
            case 12: return new NBTLongArray(slice(data, data.getInt()*8).asLongBuffer());
            default:
                throw new IllegalArgumentException("Unknown TAG=" + tag);
        }
    }

    private static<U> NBTTagList<U> parseTagList(ByteBuffer data, int tag, int len, Class<U> type) {
        NBTTagList<U> list = new NBTTagList<>(type);
        for(int idx=0 ; idx<len ; idx++)
            list.entries.add(type.cast(parseNBTValue(data, tag)));
        return list;
    }

    private static ByteBuffer slice(ByteBuffer b, int len) {
        int new_pos = b.position() + len;
        ByteBuffer ret = b.duplicate();
        ret.limit(new_pos);
        b.position(new_pos);
        return ret.slice();
    }

    private static String readUTF8(ByteBuffer b) {
        int len = b.getChar();
        int pos = b.position();
        b.position(pos + len);
        try {
            return new String(b.array(), pos, len, "UTF8");
        } catch(UnsupportedEncodingException e) {
            throw new InternalError(e);
        }
    }
}
