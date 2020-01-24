package mcworldinspector;

import java.awt.Component;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class TileEntityTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> tileEntities = Collections.emptySet();

    public TileEntityTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Tile Entities");
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
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.tileEntityTypes().anyMatch(selected::contains))
                .map(chunk -> new TileEntityHighlightEntry(chunk,
                        "Tile entity details for ", Chunk.filterByID(selected),
                         e -> MCItem.createChestView(world, e, null)));
    }

    public static Map.Entry<String, NBTTagCompound> addTileEntityLabel(NBTTagCompound tileEntity) {
        final var id = tileEntity.getString("id");
        final var pos = NBTTreeModel.formatPosition(tileEntity);
        final String label = pos.isEmpty() ? id : id + " at " + pos;
        return new AbstractMap.SimpleImmutableEntry<>(label, tileEntity);
    }

    public static class TileEntityHighlightEntry extends ChunkHighlightEntry {
        private final String titlePrefix;
        private final Predicate<NBTTagCompound> filter;
        private final Function<Map.Entry<String, NBTTagCompound>,
                Stream<? extends JComponent>> createTabs;

        public TileEntityHighlightEntry(Chunk chunk, String titlePrefix, Predicate<NBTTagCompound> filter,
                Function<Map.Entry<String, NBTTagCompound>, Stream<? extends JComponent>> createTabs) {
            super(chunk);
            this.titlePrefix = titlePrefix;
            this.filter = filter;
            this.createTabs = createTabs;
        }

        @Override
        public void showDetailsFor(Component parent) {
            final var list = chunk.tileEntities()
                    .filter(filter)
                    .map(TileEntityTypesPanel::addTileEntityLabel)
                    .collect(Collectors.toList());
            NBTTreeModel.displayNBT(parent, list, titlePrefix + this, createTabs);
        }
    }
}
