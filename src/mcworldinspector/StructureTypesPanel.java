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
public class StructureTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> structureTypes = Collections.emptySet();

    public StructureTypesPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        structureTypes = Collections.emptySet();
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
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
    protected WorldRenderer.HighlightSelector createHighlighter(List<String> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<String> structureTypes;

        public Highlighter(List<String> structureTypes) {
            this.structureTypes = structureTypes;
        }

        public List<String> getStructureTypes() {
            return structureTypes;
        }

        @Override
        public Stream<HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.structureTypes().anyMatch(structureTypes::contains))
                    .map(chunk -> new HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = structureTypes.stream()
                    .flatMap(entry.chunk::getStructures)
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Stucture details for " + entry);
        }
    }
}
