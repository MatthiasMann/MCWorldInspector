package mcworldinspector.nbt;

import java.util.Iterator;

/**
 *
 * @author matthias
 */
public abstract class NBTArray<T> extends NBTBase implements Iterable<T> {
    
    public abstract boolean isEmpty();
    public abstract int size();
    public abstract T get(int index);

    @Override
    public Iterator<T> iterator() {
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append('{');
        final int s = size();
        if(s > 0) {
            sb.append(get(0));
            for(int idx=1 ; idx<s ; idx++)
                sb.append(',').append(get(idx));
        }
        return sb.append('}').toString();
    }
}
