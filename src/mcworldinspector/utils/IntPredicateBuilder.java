package mcworldinspector.utils;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

/**
 *
 * @author matthias
 */
public abstract class IntPredicateBuilder<T> {

    public abstract T build();
    public abstract T build(int value);
    public abstract T build(int[] array, int count);
    public T build(int[] array) {
        return build(array, array.length);
    }

    public static final IntPredicate NEVER = i -> false;

    public static final class Single implements IntPredicate {
        public final int value;
        public Single(int value) {
            this.value = value;
        }
        @Override
        public boolean test(int value) {
            return value == this.value;
        }
    }

    public static IntPredicate of(int value) {
        return new Single(value);
    }

    public static IntPredicate of(int[] array) {
        return i -> {
            for(int value : array)
                if(value == i)
                    return true;
            return false;
        };
    }

    public static IntPredicate of(int[] array, int size) {
        return i -> {
            for(int idx=0 ; idx<size ; idx++)
                if(array[idx] == i)
                    return true;
            return false;
        };
    }

    public static<T> IntPredicate of(List<T> list, ToIntFunction<T> mapping) {
        switch (list.size()) {
            case 0: return NEVER;
            case 1: return of(mapping.applyAsInt(list.get(0)));
            default: return of(toArray(list, mapping));
        }
    }

    public static<T> IntPredicate of(List<T> list, Function<T, OptionalInt> mapping) {
        return of(list, mapping, BUILDER);
    }

    public static<T,R> R of(List<T> list, ToIntFunction<T> mapping, IntPredicateBuilder<R> builder) {
        switch (list.size()) {
            case 0: return builder.build();
            case 1: return builder.build(mapping.applyAsInt(list.get(0)));
            default: return builder.build(toArray(list, mapping));
        }
    }

    public static<T,R> R of(List<T> list, Function<T, OptionalInt> mapping, IntPredicateBuilder<R> builder) {
        final var size = list.size();
        for(int idx0=0 ; idx0<size ; idx0++) {
            final var value0 = mapping.apply(list.get(idx0));
            if(value0.isPresent()) {
                for(int idx1=idx0+1 ; idx1<size ; idx1++) {
                    final var value1 = mapping.apply(list.get(idx1));
                    if(value1.isPresent()) {
                        final var array = new int[size - idx1 + 1];
                        array[0] = value0.getAsInt();
                        array[1] = value1.getAsInt();
                        int count = toArray(array, 2, list, idx1 + 1, mapping);
                        return builder.build(array, count);
                    }
                }
                return builder.build(value0.getAsInt());
            }
        }
        return builder.build();
    }

    public static<R> R of(IntStream src, IntPredicateBuilder<R> builder) {
        class Collector implements IntConsumer {
            boolean hasValue0;
            int value0;
            int[] array;

            @Override
            public void accept(int value) {
                if(hasValue0) {
                    if(array == null) {
                        array = new int[8];
                        array[0] = value0;
                        value0 = 1;
                    } else if(value0 == array.length)
                        array = Arrays.copyOf(array, array.length * 2);
                    array[value0++] = value;
                } else {
                    hasValue0 = true;
                    value0 = value;
                }
            }
        }
        Collector c = new Collector();
        src.forEach(c);
        return !c.hasValue0 ? builder.build() : (c.array != null)
                ? builder.build(c.array, c.value0) : builder.build(c.value0);
    }

    private static final IntPredicateBuilder<IntPredicate> BUILDER = new IntPredicateBuilder<> () {
        @Override
        public IntPredicate build() {
            return NEVER;
        }
        @Override
        public IntPredicate build(int value) {
            return of(value);
        }
        @Override
        public IntPredicate build(int[] array) {
            return of(array);
        }
        @Override
        public IntPredicate build(int[] array, int count) {
            return of(array, count);
        }
    };

    public static<T> int[] toArray(List<T> list, ToIntFunction<T> mapping) {
        final var size = list.size();
        final var array = new int[size];
        for(int idx=0 ; idx<size ; idx++)
            array[idx] = mapping.applyAsInt(list.get(idx));
        return array;
    }

    public static<T> int[] toArray(List<T> list, Function<T, OptionalInt> mapping) {
        final var size = list.size();
        for(int idx0=0 ; idx0<size ; idx0++) {
            final var value0 = mapping.apply(list.get(idx0));
            if(value0.isPresent()) {
                final var array = new int[size - idx0];
                array[0] = value0.getAsInt();
                int count = toArray(array, 1, list, idx0 + 1, mapping);
                return count < size ? Arrays.copyOf(array, count) : array;
            }
        }
        return EMPTY_INT_ARRAY;
    }

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private static<T> int toArray(int[] array, int count, List<T> list, int idx, Function<T, OptionalInt> mapping) {
        final var size = list.size();
        for(; idx<size ; idx++) {
            final var value = mapping.apply(list.get(idx));
            if(value.isPresent())
                array[count++] = value.getAsInt();
        }
        return count;
    }
}
