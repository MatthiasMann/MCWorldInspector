package mcworldinspector.nbt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import mcworldinspector.utils.FileHelpers;
import java.util.Collection;

/**
 *
 * @author matthias
 */
public abstract class NBTTagCompound extends NBTBase implements Iterable<Map.Entry<String, Object>> {
    
    public static final NBTTagCompound EMPTY = new Empty();

    public abstract Object get(String name);
    public abstract int size();
    public abstract Set<Map.Entry<String, Object>> entrySet();
    public abstract Stream<Object> values();

    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return entrySet().iterator();
    }

    public<T> Stream<T> values(Class<T> type) {
        return values().filter(type::isInstance).map(type::cast);
    }

    public<U> U get(String name, Class<U> type) {
        Object o = get(name);
        return type.isInstance(o) ? type.cast(o) : null;
    }

    public<U> U get(String name, Class<U> type, U defaultValue) {
        Object o = get(name);
        return type.isInstance(o) ? type.cast(o) : defaultValue;
    }
    
    public IntStream getByteAsStream(String name) {
        Object o = get(name);
        return (o instanceof Byte) ? IntStream.of((Byte)o) : IntStream.empty();
    }
    
    public IntStream getIntAsStream(String name) {
        Object o = get(name);
        return (o instanceof Integer) ? IntStream.of((Integer)o) : IntStream.empty();
    }

    public String getString(String name) {
        Object o = get(name);
        return (o instanceof String) ? (String)o : null;
    }

    public Stream<String> getStringAsStream(String name) {
        Object o = get(name);
        return (o instanceof String) ? Stream.of((String)o) : Stream.empty();
    }

    public NBTTagCompound getCompound(String name) {
        Object o = get(name);
        return (o instanceof NBTTagCompound) ? (NBTTagCompound)o : EMPTY;
    }

    @SuppressWarnings("unchecked")
    public<U> NBTTagList<U> getList(String name, Class<U> type) {
        Object o = get(name);
        return (o instanceof NBTTagList) ? ((NBTTagList)o).as(type) : NBTTagList.EMPTY;
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

    public static NBTTagCompound parseInflate(ByteBuffer compressed) throws DataFormatException, IOException {
        return parseInflate(compressed, false);
    }

    public static NBTTagCompound parseGZip(ByteBuffer compressed) throws IOException, DataFormatException {
        FileHelpers.parseGZipHeader(compressed);
        return parseInflate(compressed, true);
    }

    public static NBTTagCompound parseGuess(ByteBuffer data) throws IOException, DataFormatException {
        if(data.remaining() > 3 &&
                (data.get(0) & 255) == 10 &&
                data.getChar(1) == 0)
            return parse(data);
        if(FileHelpers.isGZip(data))
            return parseGZip(data);
        return parseInflate(data, false);
    }

    private static Object parseNBTValue(ByteBuffer data, int tag) {
        switch(tag) {
            case 1: return data.get();
            case 2: return data.getShort();
            case 3: return data.getInt();
            case 4: return data.getLong();
            case 5: return data.getFloat();
            case 6: return data.getDouble();
            case 7: return slice(data, data.getInt(), NBTByteArray::new);
            case 8: return readUTF8(data);
            case 9: {
                int tagid = data.get();
                int len = data.getInt();
                switch (tagid) {
                    case 0: return NBTTagList.EMPTY;
                    case 1: return slice(data, len, NBTByteArray::new);
                    case 2: return new NBTShortArray(slice(data, len*2, ByteBuffer::asShortBuffer));
                    case 3: return new NBTIntArray(slice(data, len*4, ByteBuffer::asIntBuffer));
                    case 4: return new NBTLongArray(slice(data, len*8, ByteBuffer::asLongBuffer));
                    case 5: return new NBTFloatArray(slice(data, len*4, ByteBuffer::asFloatBuffer));
                    case 6: return new NBTDoubleArray(slice(data, len*8, ByteBuffer::asDoubleBuffer));
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
                int tagid;
                // about 10% of NBTTagCompound are empty
                if((tagid=data.get()) == 0)
                    return EMPTY;
                final String name0 = readUTF8(data).intern();
                final Object value0 = parseNBTValue(data, tagid);
                // most NBTTagCompound have only 1 entry (~50%)
                if((tagid=data.get()) == 0)
                    return new Single(name0, value0);
                final String name1 = readUTF8(data).intern();
                final Object value1 = parseNBTValue(data, tagid);
                // many NBTTagCompound have only 2 entries (~25%)
                if((tagid=data.get()) == 0)
                    return new Small(
                            new Small.Entry(name0, value0),
                            new Small.Entry(name1, value1));
                final String name2 = readUTF8(data).intern();
                final Object value2 = parseNBTValue(data, tagid);
                // keep using Small for up to 4 entries
                if((tagid=data.get()) == 0)
                    return new Small(
                            new Small.Entry(name0, value0),
                            new Small.Entry(name1, value1),
                            new Small.Entry(name2, value2));
                final String name3 = readUTF8(data).intern();
                final Object value3 = parseNBTValue(data, tagid);
                // keep using Small for up to 4 entries
                if((tagid=data.get()) == 0)
                    return new Small(
                            new Small.Entry(name0, value0),
                            new Small.Entry(name1, value1),
                            new Small.Entry(name2, value2),
                            new Small.Entry(name3, value3));
                final IdentityHashMap<String, Object> map = new IdentityHashMap<>(16);
                map.put(name0, value0);
                map.put(name1, value1);
                map.put(name2, value2);
                map.put(name3, value3);
                do {
                    String nameX = readUTF8(data).intern();
                    Object valueX = parseNBTValue(data, tagid);
                    map.put(nameX, valueX);
                } while((tagid=data.get()) != 0);
                return new Large(map);
            }
            case 11: return new NBTIntArray(slice(data, data.getInt()*4, ByteBuffer::asIntBuffer));
            case 12: return new NBTLongArray(slice(data, data.getInt()*8, ByteBuffer::asLongBuffer));
            default:
                throw new IllegalArgumentException("Unknown TAG=" + tag);
        }
    }

    private static<U> NBTTagList<U> parseTagList(ByteBuffer data, int tag, int len, Class<U> type) {
        Object[] list = new Object[len];
        for(int idx=0 ; idx<len ; idx++) {
            Object entry = parseNBTValue(data, tag);
            assert(type.isInstance(entry));
            list[idx] = entry;
        }
        return new NBTTagList<>(type, list);
    }

    private static<R> R slice(ByteBuffer b, int len, Function<ByteBuffer, R> slicer) {
        int new_pos = b.position() + len;
        int old_limit = b.limit();
        try {
            b.limit(new_pos);
            return slicer.apply(b);
        } finally {
            b.limit(old_limit);
            b.position(new_pos);
        }
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

    public static class Empty extends NBTTagCompound {
        @Override
        public Object get(String name) {
            return null;
        }
        @Override
        public int size() {
            return 0;
        }
        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            return Collections.emptySet();
        }
        @Override
        public Stream<Object> values() {
            return Stream.empty();
        }
        @Override
        public String toString() {
            return "NBTTagCompound{}";
        }
    }

    public static class Single extends NBTTagCompound implements Map.Entry<String, Object> {
        private final String key;
        private final Object value;

        Single(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Object get(String name) {
            return key.equals(name) ? value : null;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            return Collections.singleton(this);
        }

        @Override
        public Stream<Object> values() {
            return Stream.of(value);
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return key.hashCode() ^ value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)obj;
            return key.equals(e.getKey()) && value.equals(e.getValue());
        }
        @Override
        public String toString() {
            return "NBTTagCompound{" + key + '=' + value + '}';
        }
    }

    public static class Small extends NBTTagCompound implements Set<Map.Entry<String, Object>> {
        static final class Entry extends AbstractMap.SimpleImmutableEntry<String, Object> {
            Entry(String key, Object value) {
                super(key, value);
            }
        }

        private final Entry[] entries;

        Small(Entry... entries) {
            this.entries = entries;
        }

        @Override
        public Object get(String name) {
            for(Entry e : entries) {
                if(e.getKey().equals(name))
                    return e.getValue();
            }
            return null;
        }

        @Override
        public int size() {
            return entries.length;
        }

        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Map.Entry<String, Object>> iterator() {
            return (Iterator)Arrays.asList(entries).iterator();
        }

        @Override
        public Stream<Object> values() {
            return Stream.of(entries).map(Entry::getValue);
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray() {
            return Arrays.copyOf(entries, entries.length);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = entries.length;
            if (a.length < size)
                a = (T[])Array.newInstance(a.getClass().getComponentType(), size);
            System.arraycopy(entries, 0, a, 0, size);
            if (size < a.length)
                a[size] = null;
            return a;
        }

        @Override
        public boolean add(Map.Entry<String, Object> e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends Map.Entry<String, Object>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }

    public static class Large extends NBTTagCompound {
        private final IdentityHashMap<String, Object> map;

        Large(IdentityHashMap<String, Object> map) {
            this.map = map;
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            return Collections.unmodifiableSet(map.entrySet());
        }

        @Override
        public Stream<Object> values() {
            return map.values().stream();
        }

        @Override
        public String toString() {
            return "NBTTagCompound{" + map.size() + " entries" + '}';
        }
    }
}
