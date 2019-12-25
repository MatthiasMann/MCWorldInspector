package mcworldinspector.nbt;

import java.nio.ByteBuffer;

/**
 *
 * @author matthias
 */
public class NBTByteArray extends NBTArray<Byte> {
    
    private final ByteBuffer b;

    NBTByteArray(ByteBuffer b) {
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
    
    public byte getByte(int idx) {
        return b.get(idx);
    }

    @Override
    public Byte get(int idx) {
        return b.get(idx);
    }
}
