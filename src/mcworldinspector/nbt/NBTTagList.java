package mcworldinspector.nbt;

import java.util.ArrayList;
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

    public static final NBTTagList EMPTY = new NBTTagList(void.class);

    final Class type;
    final ArrayList<T> entries;

    NBTTagList(Class type) {
        this.type = type;
        entries = new ArrayList<>();
    }

    NBTTagList(Class type, ArrayList<T> entries) {
        this.type = type;
        this.entries = entries;
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
                return a -> new NBTTagList<>(type, a);
            }

            @Override
            public Set<Collector.Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public T get(int idx) {
        return entries.get(idx);
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(entries).iterator();
    }

    public Stream<T> stream() {
        return entries.stream();
    }

    @Override
    public String toString() {
        return entries.toString();
    }
    
    public<U> NBTTagList<U> as(Class<U> type) {
        return this.type.equals(type) ? (NBTTagList<U>)this : EMPTY;
    }
}
