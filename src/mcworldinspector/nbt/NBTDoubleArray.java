package mcworldinspector.nbt;

import java.nio.DoubleBuffer;

/**
 *
 * @author matthias
 */
public class NBTDoubleArray extends NBTBase implements NBTArray<Double> {
    
    private final DoubleBuffer b;

    NBTDoubleArray(DoubleBuffer b) {
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
    
    public double getDouble(int idx) {
        return b.get(idx);
    }
    
    @Override
    public Double get(int idx) {
        return b.get(idx);
    }
    
}
