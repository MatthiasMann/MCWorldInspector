package mcworldinspector;

/**
 *
 * @author matthias
 */
public class XZPosition {
    
    public final int x;
    public final int z;

    public XZPosition(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.x;
        hash = 97 * hash + this.z;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XZPosition)) {
            return false;
        }
        final XZPosition other = (XZPosition) obj;
        return this.x == other.x && this.z == other.z;
    }

    @Override
    public String toString() {
        return "XZPosition{" + "x=" + x + ", z=" + z + '}';
    }
}
