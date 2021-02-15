package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class LootChestPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> tileEntities = Collections.emptySet();

    public LootChestPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Loot Chests");
    }

    @Override
    public void reset() {
        tileEntities = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(Chunk::tileEntities)
                    .flatMap(t -> t.getStringAsStream("LootTable"))
                    .distinct()
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            tileEntities = result;
            buildListModel();
        });
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(tileEntities, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.tileEntities().flatMap(t -> t.getStringAsStream("LootTable")).anyMatch(selected::contains))
                .map(chunk -> new TileEntityTypesPanel.TileEntityHighlightEntry(chunk,
                        "Tile entity details for ",
                        t -> t.getStringAsStream("LootTable").anyMatch(lt -> selected.contains(lt)),
                        e -> MCItem.createChestView(world, e, null)));
    }
}
