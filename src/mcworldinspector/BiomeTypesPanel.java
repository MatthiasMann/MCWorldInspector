package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JCheckBox;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.IntPredicateBuilder;

/**
 *
 * @author matthias
 */
public class BiomeTypesPanel extends AbstractFilteredPanel<Biome> {
    private final ExecutorService executorService;
    private final JCheckBox btnExactShape = new JCheckBox();
    private Set<Biome> biomes = Collections.emptySet();

    public BiomeTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Biomes");

        btnExactShape.setText("Exact biome shape (slower)");
        btnExactShape.addChangeListener(e -> doHighlighting());

        horizontal.addComponent(btnExactShape);
        vertical.addComponent(btnExactShape);
    }

    @Override
    public void reset() {
        biomes = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
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
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<Biome> selected) {
        final var bm = IntPredicateBuilder.of(selected, Biome::getNumericID);
        if(btnExactShape.isSelected()) {
            return world.getChunks().parallelStream().flatMap(chunk -> {
                final var chunkBiomes = chunk.getBiomes();
                if(chunkBiomes == null)
                    return Stream.empty();
                final var og = new ChunkHighlightEntry.WithOverlay(chunk);
                for(int idx=0 ; idx<256 ; ++idx) {
                    if(bm.test(chunkBiomes.getInt(idx)))
                        og.setRGB(idx & 15, idx >> 4, 0xFFFF0000);
                }
                return og.stream();
            });
        } else {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.biomes().anyMatch(bm::test))
                    .map(ChunkHighlightEntry::new);
        }
    }
}
