package mcworldinspector.nbt;

import java.nio.LongBuffer;

/**
 *
 * @author matthias
 */
public class NBTLongArray extends NBTArray<Long> {
    
    private final long[] data;

    NBTLongArray(LongBuffer b) {
        data = new long[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public long getLong(int idx) {
        return data[idx];
    }
    
    @Override
    public Long get(int idx) {
        return data[idx];
    }
    
    public int getBits(int pos, int bits) {
        int index = pos >> 6;
        int bit = pos & 63;
        int value = (int)(data[index] >>> bit);
        if(bit + bits > 64)
            value |= (int)data[index + 1] << (64 - bit);
        return value & ((1 << bits) - 1);
    }
}
