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
public class TileEntityTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> tileEntities = Collections.EMPTY_SET;

    public TileEntityTypesPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        tileEntities = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(Chunk::tileEntityTypes)
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
    protected WorldRenderer.HighlightSelector createHighlighter(List<String> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<String> entities;

        public Highlighter(List<String> entities) {
            this.entities = entities;
        }

        public List<String> getEntities() {
            return entities;
        }

        @Override
        public Stream<HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.tileEntityTypes().anyMatch(entities::contains))
                    .map(chunk -> new HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = entities.stream()
                    .flatMap(entry.chunk::getTileEntities)
                    .filter(nbt -> !nbt.isEmpty())
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Tile entity details for " + entry);
        }
    }
}
