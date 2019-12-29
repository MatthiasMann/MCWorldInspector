package mcworldinspector;

import java.util.Random;

/**
 *
 * @author matthias
 */
public class Noise {

    private static final int[][] GRAD_3D = new int[][]{
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    private static final double SQRT_3 = Math.sqrt(3.0);

    private final int[] table = new int[256];
    private final double b;
    private final double c;
    private final double d;

    public Noise(Random random) {
        b = random.nextDouble() * 256;
        c = random.nextDouble() * 256;
        d = random.nextDouble() * 256;

        for (int i = 0; i < 256; i++)
            table[i] = i;

        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i) + i;
            int old = table[i];

            table[i] = table[j];
            table[j] = old;
        }
    }

    private static int floor(double value) {
        return value > 0.0 ? (int) value : (int) value - 1;
    }

    private static double gradient(int[] corner, double x, double y) {
        return corner[0] * x + corner[1] * y;
    }

    private static double simplex(int idx, double x, double y) {
        double t = 0.5 - x*x - y*y;
        if(t < 0)
            return 0;
        t *= t;
        return t*t * gradient(GRAD_3D[idx % 12], x, y);
    }

    public double nose2d(double x, double y) {
        double scale = (3.0 - SQRT_3) / 6.0;
        double offset = (x + y) * 0.5 * (SQRT_3 - 1.0);
        int ix = floor(x + offset);
        int iy = floor(y + offset);
        double shift = (ix + iy) * scale;
        x -= ix - shift;
        y -= iy - shift;
        int offX;
        int offY;

        if (x > y) {
            offX = 1;
            offY = 0;
        } else {
            offX = 0;
            offY = 1;
        }

        int idx0 = table[(ix +        table[ iy         & 255]) & 255];
        int idx1 = table[(ix + offX + table[(iy + offY) & 255]) & 255];
        int idx2 = table[(ix + 1    + table[(iy + 1   ) & 255]) & 255];
        
        return 70 * (
                simplex(idx0, x,                y) +
                simplex(idx1, x - offX + scale, y - offY + scale) +
                simplex(idx2, x - 1 + 2* scale, y - 1 + 2* scale));
    }
}
