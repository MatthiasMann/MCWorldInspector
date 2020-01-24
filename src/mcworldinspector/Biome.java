package mcworldinspector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author matthias
 */
public class Biome implements Comparable<Biome> {

    public static final Biome UNKNOWN = new Biome("unknown", "?", -1, 0.8f, 0.4f, 0xFF3F76E4);

    public final String name;
    public final String namespacedID;
    public final int numericID;
    public final float temperature;
    public final float rainfall;
    public final int waterColor;

    public Biome(String name, String namespaced_id, int numericID, float temperature, float rainfall, int waterColor) {
        this.name = name;
        this.namespacedID = namespaced_id;
        this.numericID = numericID;
        this.temperature = temperature;
        this.rainfall = rainfall;
        this.waterColor = waterColor;
    }

    public Biome(String namespacedID, int numericID) {
        Biome biome = VANILLA_BIOME_NAMES.getOrDefault(namespacedID, UNKNOWN);
        this.name = (biome != UNKNOWN) ? biome.name : namespacedID;
        this.namespacedID = namespacedID;
        this.numericID = numericID;
        this.temperature = biome.temperature;
        this.rainfall = biome.rainfall;
        this.waterColor = biome.waterColor;
    }

    public int getNumericID() {
        return numericID;
    }

    @Override
    public int hashCode() {
        return numericID;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Biome) {
            return ((Biome)obj).numericID == this.numericID;
        }
        return false;
    }

    @Override
    public int compareTo(Biome o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public int computeBiomeGrassColor(int elevation) {
        return biomeColor(temperature, rainfall, elevation, GRASS_CORNERS);
    }

    public int computeBiomeFoilageColor(int elevation) {
        return biomeColor(temperature, rainfall, elevation, FOLIAGE_CORNERS);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    private static final int[][] GRASS_CORNERS = {
        { 191, 183,  85 },  // lower left, temperature starts at 1.0 on left
        { 128, 180, 151 },  // lower right
        {  71, 205,  51 }}; // upper left

    private static final int[][] FOLIAGE_CORNERS = {
        { 174, 164,  42 },  // lower left, temperature starts at 1.0 on left
        {  96, 161, 123 },  // lower right
        {  26, 191,   0 }}; // upper left

    /**
     * Compute biome color based on temperature, rainfall and elevation
     * Taken from https://github.com/erich666/Mineways/blob/master/Win/biomes.cpp
     * @param temperature biome base temperature
     * @param rainfall rainfall amount
     * @param elevation Math.max(0, y - 64)
     * @param corners the biome color corners
     * @return RGB value
     */
    public static int biomeColor(float temperature, float rainfall, int elevation, int[][] corners) {
        temperature = clamp(temperature - (float)elevation*0.00166667f, 0.0f, 1.0f);
        rainfall = clamp(rainfall,0.0f,1.0f) * temperature;

        // lambda values for barycentric coordinates
        float lambda0 = temperature - rainfall;
        float lambda1 = 1.0f - temperature;
        float lambda2 = rainfall;

        int r = (int)clamp(
                lambda0 * corners[0][0] +
                lambda1 * corners[1][0] +
                lambda2 * corners[2][0], 0.0f, 255.0f);
        int g = (int)clamp(
                lambda0 * corners[0][1] +
                lambda1 * corners[1][1] +
                lambda2 * corners[2][1], 0.0f, 255.0f);
        int b = (int)clamp(
                lambda0 * corners[0][2] +
                lambda1 * corners[1][2] +
                lambda2 * corners[2][2], 0.0f, 255.0f);

        return (r<<16)|(g<<8)|b;
    }

    public static final HashMap<Integer, Biome> VANILLA_BIOMES = new HashMap<>();
    public static final HashMap<String, Biome> VANILLA_BIOME_NAMES = new HashMap<>();
    
    static {
        Pattern pattern = Pattern.compile("^([^\\t]+)\\t([^\\t]+)\\t(\\d+)\\t(-?[0-9.]+)\\t([0-9.]+)\\t([0-9a-fA-F]{6})$");
        try(InputStream is = Chunk.class.getResourceAsStream("biomes.txt");
                InputStreamReader isr = new InputStreamReader(is, "UTF8");
                BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(line -> {
                final Matcher m = pattern.matcher(line);
                if(m.matches()) {
                    final String name = m.group(1);
                    final String namespaceID = m.group(2);
                    final int numericID = Integer.parseInt(m.group(3));
                    final float temperature = Float.parseFloat(m.group(4));
                    final float rainfall = Float.parseFloat(m.group(5));
                    final int waterColor = Integer.parseInt(m.group(6), 16);
                    Biome biome = new Biome(name, namespaceID, numericID,
                            temperature, rainfall, waterColor | 0xFF000000);
                    VANILLA_BIOMES.put(biome.numericID, biome);
                    VANILLA_BIOME_NAMES.put(namespaceID, biome);
                }
            });
        } catch(Exception ex) {
        }
    }
}
