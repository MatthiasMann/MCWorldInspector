package mcworldinspector.nbt;

import java.nio.IntBuffer;

/**
 *
 * @author matthias
 */
public class NBTIntArray extends NBTBase implements NBTArray<Integer> {
    
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
    
}
