package mcworldinspector.nbt;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

/**
 *
 * @author matthias
 */
public class NBTIntArray extends NBTArray<Integer> {
    
    private final IntBuffer b;

    NBTIntArray(IntBuffer b) {
        this.b = b;
    }

    @Override
    public boolean isEmpty() {
        return !b.hasRemaining();
    }

    @Override
    public int size() {
        return b.remaining();
    }
    
    public int getInt(int idx) {
        return b.get(idx);
    }

    @Override
    public Integer get(int idx) {
        return b.get(idx);
    }
    
    public IntStream stream() {
        return IntStream.range(0, b.remaining()).map(idx -> b.get(idx));
    }
}
