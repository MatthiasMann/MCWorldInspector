package mcworldinspector.nbt;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class NBTTagList<T> extends NBTArray<T> {

    public static final NBTTagList EMPTY = new NBTTagList(void.class, new Object[0]);

    final Class type;
    final T[] entries;

    @SuppressWarnings("unchecked")
    NBTTagList(Class type, Object[] entries) {
        this.type = type;
        this.entries = (T[])entries;
    }

    @Override
    public int size() {
        return entries.length;
    }

    @Override
    public T get(int idx) {
        return entries[idx];
    }

    @Override
    public Iterator<T> iterator() {
        return Arrays.asList(entries).iterator();
    }

    public Stream<T> stream() {
        return Arrays.stream(entries);
    }

    public static class Entry<T> implements Map.Entry<Integer, T> {
        public final int index;
        public final T value;

        public Entry(int index, T value) {
            this.index = index;
            this.value = value;
        }

        @Override
        public Integer getKey() {
            return index;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public T setValue(T value) {
            throw new UnsupportedOperationException();
        }
    }

    public Stream<Entry<T>> entryStream() {
        final var e = this.entries;
        return IntStream.range(0, e.length).mapToObj(
                idx -> new Entry<>(idx, e[idx]));
    }

    @Override
    public String toString() {
        return Arrays.toString(entries);
    }

    @SuppressWarnings("unchecked")
    public<U> NBTTagList<U> as(Class<U> type) {
        return this.type.equals(type) ? (NBTTagList<U>)this : EMPTY;
    }

    @SuppressWarnings("unchecked")
    public static<U> NBTTagList<U> empty() {
        return (NBTTagList<U>)EMPTY;
    }
}
