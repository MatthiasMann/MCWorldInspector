package mcworldinspector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author matthias
 */
public class ColorPaletteGenerator implements Closeable {

    private final ArrayList<ZipFile> zipFiles = new ArrayList<>();

    private ColorPaletteGenerator(Stream<File> files) {
        files.forEach(file -> {
            try {
                zipFiles.add(new ZipFile(file));
            } catch(IOException ex) {
            }
        });
    }

    @Override
    public void close() {
        zipFiles.forEach(file -> {
            try {
                file.close();
            } catch(IOException ex) {
            }
        });
        zipFiles.clear();
    }

    public static void main(String[] args) throws IOException {
        Map<String, Color> map = buildFromTexturePack(Stream.of(
                new File("/home/matthias/.minecraft/versions/1.14.4/1.14.4.jar"),
                new File("/home/matthias/Minecraft/Colonies/mods/structurize-0.10.201-ALPHA.jar"),
                new File("/home/matthias/Minecraft/Colonies/resourcepacks/Nate's+Tweaks+0.18.2.zip")));
        try(PrintStream ps = new PrintStream("blockmap.txt")) {
            map.entrySet().stream().map(ColorPaletteGenerator::formatColorMapEntry).forEach(ps::println);
        }
    }

    public static String formatColorMapEntry(Map.Entry<String, Color> e) {
        return String.format("%06X %s", e.getValue().getRGB() & 0xFFFFFF, e.getKey());
    }

    private static final Pattern BLOCKSTATE_PATTERN = Pattern.compile("^assets/([^/]+)/blockstates/([^.]+)\\.json$");

    public static final Map<String, Color> buildFromTexturePack(Stream<File> files) throws IOException {
        try(ColorPaletteGenerator g = new ColorPaletteGenerator(files)) {
            return g.findBlockStates();
        }
    }
    
    private Map<String, Color> findBlockStates() {
        final HashMap<String, FileRef> blockStates = new HashMap<>();
        zipFiles.stream().forEach(file -> {
            file.stream().forEach(e -> {
                if(!e.isDirectory()) {
                    Matcher m = BLOCKSTATE_PATTERN.matcher(e.getName());
                    if(m.matches())
                        blockStates.put(m.group(1) + ':' + m.group(2),
                                new FileRef(file, e));
                }
            });
        });

        final HashMap<String, Color> result = new HashMap<>();
        blockStates.entrySet().stream().forEach(e -> {
            final String block = e.getKey();
            int idx = block.indexOf(':');
            String ns = block.substring(0, idx);
            Color color = processBlockState(ns, e.getValue());
            if(color != null) {
                // apply "tinting" to special blocks
                if(block.equals("minecraft:grass_block")) {
                    color = new Color(0, color.getGreen(), 0);
                } else if(block.equals("minecraft:water")) {
                    color = new Color(0, 0, color.getBlue());
                } else if(block.endsWith("_leaves")) {
                    color = new Color(0, color.getGreen(), 0);
                }
                result.put(block, color);
            }
        });
        return result;
    }
    
    private Color processBlockState(String ns, FileRef e) {
        try {
            Object o = parseJSON(e);
            if(o instanceof JSONObject) {
                JSONObject blockState = (JSONObject) o;
                if(blockState.has("variants")) {
                    final JSONObject variants = blockState.getJSONObject("variants");
                    for(String key : variants.sortedKeys()) {
                        try {
                            Object variant = variants.get(key);
                            if(variant instanceof JSONArray)
                                variant = ((JSONArray)variant).get(0);
                            if(variant instanceof JSONObject) {
                                String model = ((JSONObject)variant)
                                        .getString("model");
                                FileRef modelEntry = findEntry(ns, "models", model, ".json");
                                if(modelEntry != null)
                                    return processModel(ns, modelEntry);
                            }
                        } catch(JSONException ex) {
                        }
                    }
                } else if(blockState.has("multipart")) {
                    JSONArray multipart = blockState.getJSONArray("multipart");
                    for(int idx=0 ; idx<multipart.length() ; ++idx) {
                        try {
                            String model = multipart.getJSONObject(idx)
                                    .getJSONObject("apply").getString("model");
                            FileRef modelEntry = findEntry(ns, "models", model, ".json");
                            if(modelEntry != null)
                                return processModel(ns, modelEntry);
                        } catch(JSONException ex) {
                        }
                    }
                }
            }
        } catch(JSONException ex) {
            System.err.println(e);
            ex.printStackTrace();
        }
        return null;
    }
    
    private static final String[] TEXTURE_ORDER = {"all", "top", "end"};

    private Color processModel(String ns, FileRef e) {
        try {
            Object blockState = parseJSON(e);
            if(blockState instanceof JSONObject) {
                JSONObject textures = ((JSONObject)blockState).getJSONObject("textures");
                if(textures.length() == 0)
                    return null;
                Optional<String> textureName = Stream.of(TEXTURE_ORDER).filter(textures::has).findFirst();
                final FileRef textureEntry = findEntry(ns, "textures",
                        textures.getString(textureName.orElseGet(
                                () -> textures.sortedKeys().iterator().next())),
                        ".png");
                if(textureEntry != null)
                    return processTexture(textureEntry);
            }
        } catch(JSONException ex) {
            System.err.println(e);
            ex.printStackTrace();
        }
        return null;
    }
    
    private Color processTexture(FileRef e) {
        try(InputStream is = e.getInputStream()) {
            BufferedImage img = ImageIO.read(is);
            final int pixels = img.getWidth() * img.getHeight();
            if(pixels > 65536)
                return null;
            int red = 0;
            int green = 0;
            int blue = 0;
            for(int y=0 ; y<img.getHeight() ; y++) {
                for(int x=0 ; x<img.getWidth() ; x++) {
                    int rgb = img.getRGB(x, y);
                    red   += (rgb >> 16) & 255;
                    green += (rgb >>  8) & 255;
                    blue  += (rgb      ) & 255;
                }
            }
            return new Color(
                    divRound(red, pixels),
                    divRound(green, pixels),
                    divRound(blue, pixels));
        } catch(IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    static class FileRef {
        final ZipFile zipFile;
        final ZipEntry entry;

        FileRef(ZipFile zipFile, ZipEntry entry) {
            this.zipFile = zipFile;
            this.entry = entry;
        }

        public InputStream getInputStream() throws IOException {
            return zipFile.getInputStream(entry);
        }

        @Override
        public String toString() {
            return "FileRef{" + "zipFile=" + zipFile.getName() + ", entry=" + entry + '}';
        }
    }

    private FileRef findEntry(String ns, String path, String name, String ext) {
        int idx = name.indexOf(':');
        if(idx > 0) {
            ns = name.substring(0, idx);
            name = name.substring(idx + 1);
        }
        final String entryName = "assets/" + ns + '/' + path + '/' + name + ext;
        for(ZipFile file : zipFiles) {
            ZipEntry entry = file.getEntry(entryName);
            if(entry != null)
                return new FileRef(file, entry);
        }
        return null;
    }

    private static int divRound(int num, int denom) {
        return (num + (denom>>1)) / denom;
    }

    private static Object parseJSON(FileRef e) throws JSONException {
        try(InputStream is = e.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr)) {
            return new JSONTokener(br).nextValue();
        } catch(IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
