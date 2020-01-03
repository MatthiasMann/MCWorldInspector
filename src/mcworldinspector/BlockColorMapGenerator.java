package mcworldinspector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import javax.imageio.ImageIO;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileErrorWithExtra;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 * @author matthias
 */
public class BlockColorMapGenerator implements Closeable {

    private final List<FileError> errors;
    private final ArrayList<ZipFile> zipFiles = new ArrayList<>();

    public BlockColorMapGenerator(Stream<File> files, List<FileError> errors) {
        this.errors = errors;
        files.forEach(file -> {
            try {
                zipFiles.add(new ZipFile(file));
            } catch(IOException ex) {
                errors.add(new FileError(file, ex));
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

    private static final Pattern BLOCKSTATE_PATTERN = Pattern.compile("^assets/([^/]+)/blockstates/([^.]+)\\.json$");

    public BlockColorMap generateColorMap() {
        final HashMap<String, FileRef> blockStates = new HashMap<>();
        zipFiles.stream().forEach(file -> {
            file.stream().forEach(e -> {
                if(!e.isDirectory()) {
                    Matcher m = BLOCKSTATE_PATTERN.matcher(e.getName());
                    if(m.matches()) {
                        final String blockType = m.group(1) + ':' + m.group(2);
                        if(!"minecraft:air".equals(blockType))
                            blockStates.put(blockType, new FileRef(file, e));
                    }
                }
            });
        });

        final HashMap<String, BlockColorMap.BlockColorInfo> result = new HashMap<>();
        blockStates.entrySet().stream().forEach(e -> {
            String block = e.getKey();
            int idx = block.indexOf(':');
            String ns = block.substring(0, idx);
            Color color = processBlockState(ns, e.getValue());
            if(color != null) {
                int tinting = 0;
                // apply "tinting" to special blocks
                switch (block) {
                    case "minecraft:grass_block":
                    case "minecraft:grass":
                    case "minecraft:fern":
                    case "minecraft:vines":
                    case "minecraft:tall_grass":
                    case "minecraft:large_fern":
                        tinting = 1;
                        break;
                    case "minecraft:oak_leaves":
                    case "minecraft:dark_oak_leaves":
                    case "minecraft:acacia_leaves":
                    case "minecraft:jungle_leaves":
                        tinting = 2;
                        break;
                    case "minecraft:water":
                    case "minecraft:bubble_column":
                        tinting = 3;
                        break;
                    default:
                        break;
                }
                result.put(block, new BlockColorMap.BlockColorInfo(
                        color.getRGB(), tinting));
            }
        });
        return new BlockColorMap(result);
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
                            Object apply = multipart.getJSONObject(idx).get("apply");
                            if(apply instanceof JSONArray) {
                                JSONArray applyArray = (JSONArray)apply;
                                for(int idx2=0 ; idx2<applyArray.length() ; idx2++) {
                                    try {
                                        String model = applyArray.getJSONObject(idx2).getString("model");
                                        FileRef modelEntry = findEntry(ns, "models", model, ".json");
                                        if(modelEntry != null)
                                            return processModel(ns, modelEntry);
                                    } catch(JSONException ex) {}
                                }
                            } else if(apply instanceof JSONObject) {
                                String model = ((JSONObject)apply).getString("model");
                                FileRef modelEntry = findEntry(ns, "models", model, ".json");
                                if(modelEntry != null)
                                    return processModel(ns, modelEntry);
                            }
                        } catch(JSONException ex) {}
                    }
                }
            }
        } catch(IOException|JSONException ex) {
            errors.add(e.wrap(ex));
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
        } catch(IOException|JSONException ex) {
            errors.add(e.wrap(ex));
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
            errors.add(e.wrap(ex));
        }
        return null;
    }

    static class ZipFile extends java.util.zip.ZipFile {
        final File file;

        public ZipFile(File file) throws IOException {
            super(file);
            this.file = file;
        }
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

        public FileErrorWithExtra wrap(Exception ex) {
            return new FileErrorWithExtra(zipFile.file, ex, entry.getName());
        }
    }

    private FileRef findEntry(String ns, String path, String name, String ext) {
        int idx = name.indexOf(':');
        if(idx > 0) {
            ns = name.substring(0, idx);
            name = name.substring(idx + 1);
        }
        if(ns.equals("minecraft") && name.equals("block/air"))
            return null;
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

    private static Object parseJSON(FileRef e) throws IOException, JSONException {
        try(InputStream is = e.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr)) {
            return new JSONTokener(br).nextValue();
        }
    }
}
