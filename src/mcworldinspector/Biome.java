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
    public final String name;
    public final String namespacedID;
    public final int numericID;

    public Biome(String name, String namespaced_id, int numeric_id) {
        this.name = name;
        this.namespacedID = namespaced_id;
        this.numericID = numeric_id;
    }

    public Biome(String namespacedID, int numericID) {
        this.name = VANILLA_BIOME_NAMES.getOrDefault(namespacedID, namespacedID);
        this.namespacedID = namespacedID;
        this.numericID = numericID;
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
    
    public static final HashMap<Integer, Biome> VANILLA_BIOMES = new HashMap<>();
    public static final HashMap<String, String> VANILLA_BIOME_NAMES = new HashMap<>();
    
    static {
        Pattern pattern = Pattern.compile("^([^\\t]+)\\t([^\\t]+)\\t(\\d+)$");
        try(InputStream is = Chunk.class.getResourceAsStream("biomes.txt");
                InputStreamReader isr = new InputStreamReader(is, "UTF8");
                BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(line -> {
                final Matcher m = pattern.matcher(line);
                if(m.matches()) {
                    final String name = m.group(1);
                    final String namespaceID = m.group(2);
                    final int numericID = Integer.parseInt(m.group(3));
                    Biome biome = new Biome(name, namespaceID, numericID);
                    VANILLA_BIOMES.put(biome.numericID, biome);
                    VANILLA_BIOME_NAMES.put(namespaceID, name);
                }
            });
        } catch(Exception ex) {
        }
    }
}
