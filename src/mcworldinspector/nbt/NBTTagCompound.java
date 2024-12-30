package mcworldinspector.nbt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import mcworldinspector.utils.FileHelpers;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;
import mcworldinspector.utils.AbstractSpliterator;

/**
 *
 * @author matthias
 */
public abstract class NBTTagCompound extends NBTBase {
    
    public static final NBTTagCompound EMPTY = new Empty();

    public abstract Object get(String name);
    public abstract int size();
    public abstract Stream<Map.Entry<String, Object>> entries();
    public abstract Stream<Object> values();

    public Object get(String[] names) {
        for (String name : names) {
            final var o = get(name);
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return size() == 0;
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

    public NBTTagCompound getCompound(String[] names) {
        Object o = get(names);
        return (o instanceof NBTTagCompound) ? (NBTTagCompound)o : EMPTY;
    }

    @SuppressWarnings("unchecked")
    public<U> NBTTagList<U> getList(String name, Class<U> type) {
        Object o = get(name);
        return (o instanceof NBTTagList) ? ((NBTTagList)o).as(type) : NBTTagList.EMPTY;
    }

    @SuppressWarnings("unchecked")
    public<U> NBTTagList<U> getList(String[] names, Class<U> type) {
        Object o = get(names);
        return (o instanceof NBTTagList) ? ((NBTTagList)o).as(type) : NBTTagList.EMPTY;
    }

    public static NBTTagCompound of(String key0, Object value0) {
        return new Single(key0, value0);
    }

    public static NBTTagCompound parse(ByteBuffer data) {
        if(data.get() != 10)
            throw new IllegalArgumentException("Root tag must be an NBTTagCompound");
        if(data.getChar() != 0)
            throw new IllegalArgumentException("Root tag must not have a name");
        return parseTagCompound(data);
    }

    public static NBTTagCompound parseInflate(ByteBuffer compressed, ByteBuffer uncompressed, boolean unwrap) throws DataFormatException, IOException {
        if (unwrap)
            FileHelpers.parseGZipHeader(compressed);
        int remaining = uncompressed.remaining();
        Inflater i = new Inflater(unwrap);
        i.setInput(compressed);
        i.inflate(uncompressed);
        if(!i.finished())
            throw new IOException("NBT data bigger than " + remaining + " bytes");
        uncompressed.flip();
        return parse(uncompressed);
    }

    public static NBTTagCompound parseInflate(ByteBuffer compressed, boolean unwarp) throws DataFormatException, IOException {
        return parseInflate(compressed, ByteBuffer.allocateDirect(8 << 20), unwarp);
    }

    public static NBTTagCompound parseInflate(ByteBuffer compressed) throws DataFormatException, IOException {
        return parseInflate(compressed, false);
    }

    public static NBTTagCompound parseGZip(ByteBuffer compressed) throws IOException, DataFormatException {
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
                /* statistics:
                 *   77% tagid = 0
                 *   17% tagid = 10
                 *    4% tagid = 9
                 */
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
                    case 10: return parseTagListCompound(data, len);
                    case 11: return parseTagList(data, tagid, len, NBTIntArray.class);
                    case 12: return parseTagList(data, tagid, len, NBTLongArray.class);
                    default:
                        throw new IllegalArgumentException("Unknown TAG=" + tagid);
                }
            }
            case 10: return parseTagCompound(data);
            case 11: return new NBTIntArray(slice(data, data.getInt()*4, ByteBuffer::asIntBuffer));
            case 12: return new NBTLongArray(slice(data, data.getInt()*8, ByteBuffer::asLongBuffer));
            default:
                throw new IllegalArgumentException("Unknown TAG=" + tag);
        }
    }

    private static NBTTagCompound parseTagCompound(ByteBuffer data) {
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
            return new Small(name0, value0, name1, value1);
        final String name2 = readUTF8(data).intern();
        final Object value2 = parseNBTValue(data, tagid);
        // keep using Small for up to 4 entries
        if((tagid=data.get()) == 0)
            return new Small(name0, value0, name1, value1, name2, value2);
        final String name3 = readUTF8(data).intern();
        final Object value3 = parseNBTValue(data, tagid);
        // keep using Small for up to 4 entries
        if((tagid=data.get()) == 0)
            return new Small(name0, value0, name1, value1,
                    name2, value2, name3, value3);
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

    private static<U> NBTTagList<U> parseTagList(ByteBuffer data, int tag, int len, Class<U> type) {
        Object[] list = new Object[len];
        for(int idx=0 ; idx<len ; idx++) {
            Object entry = parseNBTValue(data, tag);
            assert(type.isInstance(entry));
            list[idx] = entry;
        }
        return new NBTTagList<>(type, list);
    }

    private static<U> NBTTagList<U> parseTagListCompound(ByteBuffer data, int len) {
        Object[] list = new Object[len];
        for(int idx=0 ; idx<len ; idx++)
            list[idx] = parseTagCompound(data);
        return new NBTTagList<>(NBTTagCompound.class, list);
    }

    private static<R> R slice(ByteBuffer b, int len, Function<ByteBuffer, R> slicer) {
        int new_pos = b.position() + len;
        int old_limit = b.limit();
        try {
            b.limit(new_pos);
            return slicer.apply(b);
        } finally {
            b.limit(old_limit).position(new_pos);
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF8");

    private static String readUTF8(ByteBuffer b) {
        int len = b.getChar();
        if(!b.isDirect()) {
            final int pos = b.position();
            b.position(pos + len);
            return new String(b.array(), pos, len, UTF8);
        } else if(len <= 32) {
            // 99% of all strings are 32 or less bytes long
            final byte[] tmp = new byte[len];
            b.get(tmp, 0, len);
            return new String(tmp, UTF8);
        } else
            return slice(b, len, sb -> UTF8.decode(sb).toString());
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
        public Stream<Entry<String, Object>> entries() {
            return Stream.empty();
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
        public Stream<Entry<String, Object>> entries() {
            return Stream.<Map.Entry<String, Object>>of(this);
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

    public static class Small extends NBTTagCompound {
        private final Object[] storage;

        Small(Object... data) {
            this.storage = data;
        }

        @Override
        public Object get(String name) {
            for(int idx=0,size=storage.length ; idx<size ; idx+=2) {
                if(storage[idx].equals(name))
                    return storage[idx+1];
            }
            return null;
        }

        @Override
        public int size() {
            return storage.length / 2;
        }

        @Override
        public Stream<Entry<String, Object>> entries() {
            return StreamSupport.stream(new EntrySetIterator(storage), false);
        }

        @Override
        public Stream<Object> values() {
            return StreamSupport.stream(new ValueIterator(storage), false);
        }

        private static abstract class SmallIteratorBase<T> extends AbstractSpliterator<T> {
            protected final Object[] storage;
            protected int index;

            SmallIteratorBase(Object[] storage) {
                this.storage = storage;
            }

            @Override
            public boolean hasNext() {
                return index < storage.length;
            }

            @Override
            public long estimateSize() {
                return storage.length / 2;
            }

            @Override
            public int characteristics() {
                return ORDERED | SIZED | NONNULL | IMMUTABLE | DISTINCT;
            }
        }

        private static class EntrySetIterator extends SmallIteratorBase<Map.Entry<String, Object>> {
            EntrySetIterator(Object[] storage) {
                super(storage);
            }

            @Override
            public Map.Entry<String, Object> next() {
                if(!hasNext())
                    throw new NoSuchElementException();
                final Object[] s = storage;
                final int idx = index += 2;
                return new AbstractMap.SimpleImmutableEntry<>(
                        (String)s[idx-2], s[idx-1]);
            }
        }

        private static class ValueIterator extends SmallIteratorBase<Object> {
            ValueIterator(Object[] storage) {
                super(storage);
            }

            @Override
            public Object next() {
                if(!hasNext())
                    throw new NoSuchElementException();
                int idx = index += 2;
                return storage[idx - 1];
            }
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
        public Stream<Entry<String, Object>> entries() {
            return map.entrySet().stream();
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
