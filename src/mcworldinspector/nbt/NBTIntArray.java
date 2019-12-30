package mcworldinspector.nbt;

import java.nio.IntBuffer;
import java.util.stream.IntStream;

/**
 *
 * @author matthias
 */
public class NBTIntArray extends NBTArray<Integer> {
    
    private final int[] data;

    NBTIntArray(IntBuffer b) {
        data = new int[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public int getInt(int idx) {
        return data[idx];
    }

    @Override
    public Integer get(int idx) {
        return data[idx];
    }
    
    public IntStream stream() {
        return IntStream.of(data);
    }
}
