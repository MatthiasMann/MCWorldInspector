package mcworldinspector;

/**
 *
 * @author matthias
 */
public class BlockPos implements Comparable<BlockPos> {
    public final int x;
    public final int y;
    public final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return "X=" + x + " Y=" + y + " Z=" + z;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.x;
        hash = 89 * hash + this.y;
        hash = 89 * hash + this.z;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof BlockPos) {
            BlockPos o = (BlockPos)obj;
            return x == o.x && y == o.y && z == o.z;
        }
        return false;
    }

    @Override
    public int compareTo(BlockPos o) {
        int diff = Integer.compare(y, o.y);
        if(diff == 0)
            diff = Integer.compare(x, o.x);
        if(diff == 0)
            diff = Integer.compare(z, o.z);
        return diff;
    }
}
