package mcworldinspector.nbt;

import java.util.Iterator;

/**
 *
 * @author matthias
 */
public interface NBTArray<T> extends Iterable<T> {
    
    public abstract boolean isEmpty();
    public abstract int size();
    public abstract T get(int index);

    @Override
    public default Iterator<T> iterator() {
        return new Iterator<T>() {
            private int pos;
            @Override
            public boolean hasNext() {
                return pos < size();
            }

            @Override
            public T next() {
                return get(pos++);
            }
        };
    }

}
