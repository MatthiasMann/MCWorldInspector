package mcworldinspector.nbt;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author matthias
 */
public class NBTTagCompound extends NBTBase implements Iterable<Map.Entry<String, Object>> {
    final IdentityHashMap<String, Object> entries = new IdentityHashMap<>();

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

    public<U> U get(String name, Class<U> type) {
        Object o = entries.get(name);
        return type.isInstance(o) ? type.cast(o) : null;
    }

    public String getString(String name) {
        Object o = entries.get(name);
        return (o instanceof String) ? (String)o : null;
    }

    public NBTTagCompound getCompound(String name) {
        Object o = entries.get(name);
        return (o instanceof NBTTagCompound) ? (NBTTagCompound)o : null;
    }

    public<U> NBTTagList<U> getList(String name, Class<U> type) {
        Object o = entries.get(name);
        return (o instanceof NBTTagList) ? ((NBTTagList)o).as(type) : null;
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    public static NBTTagCompound parse(ByteBuffer chunk) {
        if(chunk.get() != 10)
            throw new IllegalArgumentException("Root tag must be an NBTTagCompound");
        if(chunk.getChar() != 0)
            throw new IllegalArgumentException("Root tag must not have a name");
        return (NBTTagCompound)parseNBTValue(chunk, 10);
    }

    private static Object parseNBTValue(ByteBuffer chunk, int tag) {
        switch(tag) {
            case 1: return chunk.get();
            case 2: return chunk.getShort();
            case 3: return chunk.getInt();
            case 4: return chunk.getLong();
            case 5: return chunk.getFloat();
            case 6: return chunk.getDouble();
            case 7: return new NBTByteArray(slice(chunk, chunk.getInt()));
            case 8: return readUTF8(chunk);
            case 9: {
                int tagid = chunk.get();
                int len = chunk.getInt();
                switch (tagid) {
                    case 0: return null;
                    case 1: return new NBTByteArray(slice(chunk, len));
                    case 2: return new NBTShortArray(slice(chunk, len*2).asShortBuffer());
                    case 3: return new NBTIntArray(slice(chunk, len*4).asIntBuffer());
                    case 4: return new NBTLongArray(slice(chunk, len*8).asLongBuffer());
                    case 5: return new NBTFloatArray(slice(chunk, len*4).asFloatBuffer());
                    case 6: return new NBTDoubleArray(slice(chunk, len*8).asDoubleBuffer());
                    case 7: return parseTagList(chunk, tagid, len, NBTByteArray.class);
                    case 8: return parseTagList(chunk, tagid, len, String.class);
                    case 9: return parseTagList(chunk, tagid, len, NBTArray.class);
                    case 10: return parseTagList(chunk, tagid, len, NBTTagCompound.class);
                    case 11: return parseTagList(chunk, tagid, len, NBTIntArray.class);
                    case 12: return parseTagList(chunk, tagid, len, NBTLongArray.class);
                    default:
                        throw new IllegalArgumentException("Unknown TAG=" + tagid);
                }
            }
            case 10: {
                NBTTagCompound map = new NBTTagCompound();
                int tagid;
                while((tagid=chunk.get()) != 0) {
                    String name = readUTF8(chunk).intern();
                    map.entries.put(name, parseNBTValue(chunk, tagid));
                }
                return map;
            }
            case 11: return new NBTIntArray(slice(chunk, chunk.getInt()*4).asIntBuffer());
            case 12: return new NBTLongArray(slice(chunk, chunk.getInt()*8).asLongBuffer());
            default:
                throw new IllegalArgumentException("Unknown TAG=" + tag);
        }
    }

    private static<U> NBTTagList<U> parseTagList(ByteBuffer chunk, int tag, int len, Class<U> type) {
        NBTTagList<U> list = new NBTTagList<>(type);
        for(int idx=0 ; idx<len ; idx++)
            list.entries.add(type.cast(parseNBTValue(chunk, tag)));
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
