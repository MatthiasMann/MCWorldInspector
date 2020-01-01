package mcworldinspector.utils;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public @FunctionalInterface interface AsyncExecution<R> {

    public R asyncExecute() throws Exception;

    public static<R> void submit(Executor executor, AsyncExecution<R> asyncExec, Consumer<Expected<R>> completed) {
        executor.execute(() -> {
            final Expected<R> result = Expected.wrapAsync(asyncExec);
            EventQueue.invokeLater(() -> completed.accept(result));
        });
    }

    public static<R> void submitNoThrow(Executor executor, Supplier<R> asyncExec, Consumer<R> completed) {
        executor.execute(() -> {
            final R result = asyncExec.get();
            EventQueue.invokeLater(() -> completed.accept(result));
        });
    }

    public static<R> int submit(Executor executor, Stream<AsyncExecution<R>> tasks, Consumer<List<Expected<R>>> completed) {
        final ArrayList<Expected<R>> results = new ArrayList<>();
        final AsyncPendingCounter pending = new AsyncPendingCounter(
                () -> completed.accept(results), 1);
        final int submitted = tasks.mapToInt(task -> {
            if(task == null)
                return 0;
            try {
                pending.incrementAndGet();
                executor.execute(() -> {
                    final Expected result = Expected.wrapAsync(task);
                    synchronized (results) {
                        if(results.isEmpty())
                            results.ensureCapacity(pending.get());
                        results.add(result);
                    }
                    pending.decrement();
                });
                return 1;
            } catch(Exception ex) {
                pending.decrement();
                throw ex;
            }
        }).sum();
        pending.decrement();
        return submitted;
    }
}
