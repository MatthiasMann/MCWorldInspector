package mcworldinspector.nbt;

import java.nio.DoubleBuffer;

/**
 *
 * @author matthias
 */
public class NBTDoubleArray extends NBTArray<Double> {
    
    private final double[] data;

    NBTDoubleArray(DoubleBuffer b) {
        data = new double[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public double getDouble(int idx) {
        return data[idx];
    }
    
    @Override
    public Double get(int idx) {
        return data[idx];
    }
    
}
