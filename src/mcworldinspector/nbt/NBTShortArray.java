package mcworldinspector.nbt;

import java.nio.ShortBuffer;

/**
 *
 * @author matthias
 */
public class NBTShortArray extends NBTArray<Short> {
    
    private final short[] data;

    NBTShortArray(ShortBuffer b) {
        data = new short[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public short getShort(int idx) {
        return data[idx];
    }

    @Override
    public Short get(int idx) {
        return data[idx];
    }

}
