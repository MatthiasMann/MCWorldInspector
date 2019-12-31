package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class BiomeTypesPanel extends AbstractFilteredPanel<Biome> {
    private final ExecutorService executorService;
    private World world;
    private Set<Biome> biomes = Collections.EMPTY_SET;

    public BiomeTypesPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        world = null;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        this.world = world;
        final Map<Integer, Biome> biomeRegistry = world.getBiomeRegistry();
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(c -> c.biomes(biomeRegistry))
                .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            biomes = result;
            buildListModel();
        });
    }

    @Override
    protected List<Biome> filteredList(String filter) {
        return biomes.stream().filter(e ->
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
        public Stream<HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.biomes(biomeRegistry)
                            .anyMatch(biomes::contains))
                    .map(chunk -> new HighlightEntry(chunk));
        }
    }
}
