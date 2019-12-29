package mcworldinspector.utils;

import java.awt.EventQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public interface AsyncTask<R> {

    public R asyncExecute() throws Exception;

    public void completed(Expected<R> result);

    public static<R> void submit(Executor executor, Expected.ThrowingSupplier<R> asyncExec, Consumer<Expected<R>> completed) {
        executor.execute(() -> {
            final Expected<R> result = Expected.wrap(asyncExec);
            EventQueue.invokeLater(() -> completed.accept(result));
        });
    }

    public static void submit(Executor executor, AsyncTask<?> task) {
        executor.execute(() -> {
            final Expected result = Expected.wrapAsync(task);
            EventQueue.invokeLater(() -> task.completed(result));
        });
    }

    public static int submit(Executor executor, Stream<? extends AsyncTask<?>> tasks, Runnable allCompleted) {
        final AtomicInteger pending = new AtomicInteger();
        int submitted = tasks.mapToInt(task -> {
            try {
                pending.incrementAndGet();
                executor.execute(() -> {
                    final Expected result = Expected.wrapAsync(task);
                    EventQueue.invokeLater(() -> {
                        try {
                            task.completed(result);
                        } finally {
                            if(pending.decrementAndGet() == 0)
                                allCompleted.run();
                        }
                    });
                });
                return 1;
            } catch(Exception ex) {
                pending.decrementAndGet();
                throw ex;
            }
        }).sum();
        if(submitted == 0)
            EventQueue.invokeLater(allCompleted);
        return submitted;
    }
}
