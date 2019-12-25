package mcworldinspector.nbt;

import java.nio.LongBuffer;

/**
 *
 * @author matthias
 */
public class NBTLongArray extends NBTArray<Long> {
    
    private final LongBuffer b;

    NBTLongArray(LongBuffer b) {
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
    
    public long getLong(int idx) {
        return b.get(idx);
    }
    
    @Override
    public Long get(int idx) {
        return b.get(idx);
    }
    
    public int getBits(int pos, int bits) {
        int index = pos >> 6;
        int bit = pos & 63;
        int value = (int)(b.get(index) >>> bit);
        if(bit + bits > 64)
            value |= (int)b.get(index + 1) << (64 - bit);
        return value & ((1 << bits) - 1);
    }
}
