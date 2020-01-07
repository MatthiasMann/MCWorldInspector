package mcworldinspector;

import java.awt.Component;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class StructureTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> structureTypes = Collections.emptySet();

    public StructureTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        structureTypes = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(Chunk::structureTypes)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            structureTypes = result;
            buildListModel();
        });
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(structureTypes, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.structureTypes().anyMatch(selected::contains))
                .map(chunk -> new ChunkHighlightEntry(chunk) {
                    @Override
                    public void showDetailsFor(Component parent) {
                        final var list = chunk.structures()
                                .filter(Chunk.filterByID(selected))
                                .map(StructureTypesPanel::addStructureLabel)
                                .collect(Collectors.toList());
                        final var model = list.size() == 1
                                ? new NBTTreeModel(list.get(0).getValue())
                                : new NBTTreeModel(list);
                        NBTTreeModel.displayNBT(parent, model,
                                "Stucture details for " + this);
                    }
                });
    }

    public static Map.Entry<String, NBTTagCompound> addStructureLabel(NBTTagCompound s) {
        return new AbstractMap.SimpleImmutableEntry<>("?", s);
    }
}
