package mcworldinspector.utils;

/**
 *
 * @author matthias
 */
public class Expected<T> {
    final Object value;

    public Expected(T value) {
        this.value = value;
    }

    public Expected(Exception value) {
        this.value = value;
    }
    
    public T get() throws Exception {
        if(value instanceof Exception)
            throw (Exception)value;
        return (T)value;
    }
}
