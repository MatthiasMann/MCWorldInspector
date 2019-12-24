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
    
    public @FunctionalInterface interface ThrowingSupplier<T> {
        public T get() throws Exception;
    };

    public static<T> Expected<T> wrap(ThrowingSupplier<T> producer) {
        try {
            return new Expected<>(producer.get());
        } catch(Exception ex) {
            return new Expected<>(ex);
        }
    }
}
