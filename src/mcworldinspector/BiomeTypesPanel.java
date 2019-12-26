package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class BiomeTypesPanel extends AbstractFilteredPanel<Biome> {
    private World world;

    public BiomeTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        world = null;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        this.world = world;
        buildListModel();
    }

    @Override
    protected List<Biome> filteredList(String filter) {
        if(world == null)
            return Collections.EMPTY_LIST;
        return world.getBiomes().stream().filter(e ->
            filter.isEmpty() || e.name.contains(filter)).collect(Collectors.toList());
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<Biome> selected) {
        return new Highlighter(world.getBiomeRegistry(), selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final Map<Integer, Biome> biomeRegistry;
        private final List<Biome> biomes;

        public Highlighter(Map<Integer, Biome> biomeRegistry, List<Biome> biomes) {
            this.biomeRegistry = biomeRegistry;
            this.biomes = biomes;
        }

        @Override
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.biomes(biomeRegistry)
                            .anyMatch(biomes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }
    }
}
