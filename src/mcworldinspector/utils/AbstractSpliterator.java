package mcworldinspector.utils;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 *
 * @author matthias
 */
public abstract class AbstractSpliterator<T> implements Spliterator<T>, Iterator<T> {

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if(hasNext()) {
            action.accept(next());
            return true;
        }
        return false;
    }

    @Override
    public Spliterator<T> trySplit() {
        return null;
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
        while(hasNext())
            action.accept(next());
    }

}
