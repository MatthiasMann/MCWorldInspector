package mcworldinspector.utils;

import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author matthias
 */
public class AsyncPendingCounter extends AtomicInteger {

    private final Runnable completed;

    public AsyncPendingCounter(Runnable completed, int initialValue) {
        super(initialValue);
        this.completed = completed;
    }

    public AsyncPendingCounter(Runnable completed) {
        this.completed = completed;
    }

    public void decrement() {
        if(decrementAndGet() == 0)
            EventQueue.invokeLater(completed);
    }
}
