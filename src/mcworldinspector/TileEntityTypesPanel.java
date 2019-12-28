package mcworldinspector;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;

/**
 *
 * @author matthias
 */
public class TileEntityTypesPanel extends AbstractFilteredPanel<String> {
    private Set<String> tileEntities = Collections.EMPTY_SET;

    public TileEntityTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        tileEntities = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        tileEntities = world.getTileEntityTypes();
        buildListModel();
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
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.tileEntityTypes().anyMatch(entities::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, WorldRenderer.HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = entities.stream()
                    .flatMap(entry.chunk::getTileEntities)
                    .filter(nbt -> !nbt.isEmpty())
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Tile entity details for " + entry);
        }
    }
}