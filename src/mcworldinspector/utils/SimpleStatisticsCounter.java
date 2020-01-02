package mcworldinspector.utils;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author matthias
 */
public class SimpleStatisticsCounter<K> {

    private final HashMap<K, Long> counts = new HashMap<>();

    public synchronized void count(K key) {
        counts.compute(key, (k,v) -> (v == null) ? 1 : v + 1);
    }

    public synchronized void print(PrintStream ps) {
        final long total = counts.entrySet().stream()
                .mapToLong(Map.Entry::getValue).sum();
        counts.entrySet().stream().sorted((a,b) -> {
            return Long.compare(a.getValue(), b.getValue());
        }).forEachOrdered(e -> {
            System.out.printf("%7d times (%5.2f%%): %s\n", e.getValue(),
                    e.getValue() * 100.0 / total, e.getKey());
        });
    }
}
