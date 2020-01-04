package mcworldinspector;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class EntityTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> entities = Collections.emptySet();

    public EntityTypesPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        entities = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(Chunk::entityTypes)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            entities = result;
            buildListModel();
        });
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(entities, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.entityTypes().anyMatch(selected::contains))
                .map(chunk -> new ChunkHighlightEntry(chunk) {
                    @Override
                    public void showDetailsFor(Component parent) {
                        NBTTagList<NBTTagCompound> result = chunk.entities()
                                .filter(Chunk.filterByID(selected))
                                .collect(NBTTagList.toTagList(NBTTagCompound.class));
                        NBTTreeModel.displayNBT(parent, result, "Entity details for " + this);
                    }
                });
    }
}
