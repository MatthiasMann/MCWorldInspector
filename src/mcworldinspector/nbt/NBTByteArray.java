package mcworldinspector.nbt;

import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;

/**
 *
 * @author matthias
 */
public class NBTByteArray extends NBTArray<Byte> {
    
    private final byte[] data;

    NBTByteArray(ByteBuffer b) {
        data = new byte[b.remaining()];
        b.get(data);
    }

    @Override
    public int size() {
        return data.length;
    }
    
    public byte getByte(int idx) {
        return data[idx];
    }

    @Override
    public Byte get(int idx) {
        return data[idx];
    }

    public void setDataElements(WritableRaster r, int x, int y, int w, int h) {
        r.setDataElements(x, y, w, h, data);;
    }
}
