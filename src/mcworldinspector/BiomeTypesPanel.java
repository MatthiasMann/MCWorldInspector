package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class BiomeTypesPanel extends AbstractFilteredPanel<Chunk.Biome> {
    private Set<Chunk.Biome> biomes = Collections.EMPTY_SET;

    public BiomeTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        biomes = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        biomes = world.getBiomes();
        buildListModel();
    }

    @Override
    protected List<Chunk.Biome> filteredList(String filter) {
        return biomes.stream().filter(e ->
            filter.isEmpty() || e.name.contains(filter)).collect(Collectors.toList());
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<Chunk.Biome> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<Chunk.Biome> biomes;

        public Highlighter(List<Chunk.Biome> biomes) {
            this.biomes = biomes;
        }

        public List<Chunk.Biome> getBiomes() {
            return biomes;
        }

        @Override
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.biomes().anyMatch(biomes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }
    }
}
