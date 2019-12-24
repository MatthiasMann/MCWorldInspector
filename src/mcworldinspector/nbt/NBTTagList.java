package mcworldinspector.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class NBTTagList<T> extends NBTBase implements NBTArray<T> {

    public static final NBTTagList EMPTY = new NBTTagList(void.class);

    final Class type;
    final ArrayList<T> entries = new ArrayList<>();

    NBTTagList(Class type) {
        this.type = type;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

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
