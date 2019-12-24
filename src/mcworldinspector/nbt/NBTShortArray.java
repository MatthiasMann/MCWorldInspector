package mcworldinspector.nbt;

import java.nio.ShortBuffer;

/**
 *
 * @author matthias
 */
public class NBTShortArray extends NBTBase implements NBTArray<Short> {
    
    private final ShortBuffer b;

    NBTShortArray(ShortBuffer b) {
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
    
    public short getShort(int idx) {
        return b.get(idx);
    }

    @Override
    public Short get(int idx) {
        return b.get(idx);
    }

}
