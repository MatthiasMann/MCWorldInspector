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
                .map(chunk -> new ChunkHighlightEntry(chunk) {
                    @Override
                    public void showDetailsFor(Component parent) {
                        final var list = chunk.tileEntities()
                                .filter(Chunk.filterByID(selected))
                                .map(TileEntityTypesPanel::addTileEntityLabel)
                                .collect(Collectors.toList());
                        NBTTreeModel.displayNBT(parent, new NBTTreeModel(list),
                                "Tile entity details for " + this);
                    }
                });
    }

    public static Map.Entry<String, NBTTagCompound> addTileEntityLabel(NBTTagCompound tileEntity) {
        final var id = tileEntity.getString("id");
        final var pos = NBTTreeModel.formatPosition(tileEntity);
        final String label = pos.isEmpty() ? id : id + " at " + pos;
        return new AbstractMap.SimpleImmutableEntry<>(label, tileEntity);
    }
}
