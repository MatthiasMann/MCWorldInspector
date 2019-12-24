package mcworldinspector.nbt;

import java.nio.FloatBuffer;

/**
 *
 * @author matthias
 */
public class NBTFloatArray extends NBTBase implements NBTArray<Float> {
    
    private final FloatBuffer b;

    NBTFloatArray(FloatBuffer b) {
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
    
    public float getFloat(int idx) {
        return b.get(idx);
    }
    
    @Override
    public Float get(int idx) {
        return b.get(idx);
    }
    
}
