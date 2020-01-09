package mcworldinspector;

import java.awt.Component;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class EntityTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> entities = Collections.emptySet();

    public EntityTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Entities");
    }

    @Override
    public void reset() {
        entities = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
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
        return createHighlighter(world, Chunk.filterByID(selected),
                "Entity details for ");
    }

    public static Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(
            World world, Predicate<NBTTagCompound> filter, String titlePrefix) {
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.entities().anyMatch(filter))
                .map(chunk -> new ChunkHighlightEntry(chunk) {
                    @Override
                    public void showDetailsFor(Component parent) {
                        final var list = chunk.entities()
                                .filter(filter)
                                .map(EntityTypesPanel::addEntityLabel)
                                .collect(Collectors.toList());
                        NBTTreeModel.displayNBT(parent, new NBTTreeModel(list),
                                titlePrefix + this);
                    }
                });
    }

    public static Map.Entry<String, NBTTagCompound> addEntityLabel(NBTTagCompound entity) {
        final var id = entity.getString("id");
        final var name = entity.getString("CustomName");
        final var pos = entity.get("Pos", NBTDoubleArray.class);
        final var labelBase = (name == null || name.isEmpty()) ? id : name;
        final String label;
        if(pos != null && pos.size() == 3)
            label = labelBase + " at " + NBTTreeModel.formatPosition(pos);
        else
            label = labelBase;
        return new AbstractMap.SimpleImmutableEntry<>(label, entity);
    }
}
