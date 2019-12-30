package mcworldinspector.nbt;

import java.nio.FloatBuffer;

/**
 *
 * @author matthias
 */
public class NBTFloatArray extends NBTArray<Float> {
    
    private final float[] data;

    NBTFloatArray(FloatBuffer b) {
        data = new float[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public float getFloat(int idx) {
        return data[idx];
    }
    
    @Override
    public Float get(int idx) {
        return data[idx];
    }
    
}
