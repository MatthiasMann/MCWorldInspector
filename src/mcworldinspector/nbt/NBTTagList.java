package mcworldinspector.nbt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class NBTTagList<T> extends NBTArray<T> {

    public static final NBTTagList EMPTY = new NBTTagList(void.class, new Object[0]);

    final Class type;
    final T[] entries;

    NBTTagList(Class type, Object[] entries) {
        this.type = type;
        this.entries = (T[])entries;
    }

    public static<T> Collector<T, ArrayList<T>, NBTTagList<T>> toTagList(Class<T> type) {
        return new Collector<T, ArrayList<T>, NBTTagList<T>>() {
            @Override
            public Supplier<ArrayList<T>> supplier() {
                return () -> new ArrayList<>();
            }

            @Override
            public BiConsumer<ArrayList<T>, T> accumulator() {
                return ArrayList::add;
            }

            @Override
            public BinaryOperator<ArrayList<T>> combiner() {
                return (l,r) -> { l.addAll(r); return l; };
            }

            @Override
            public Function<ArrayList<T>, NBTTagList<T>> finisher() {
                return a -> new NBTTagList<>(type, a.toArray());
            }

            @Override
            public Set<Collector.Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
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

    @Override
    public String toString() {
        return Arrays.toString(entries);
    }
    
    public<U> NBTTagList<U> as(Class<U> type) {
        return this.type.equals(type) ? (NBTTagList<U>)this : EMPTY;
    }
}
