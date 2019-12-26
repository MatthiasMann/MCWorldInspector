package mcworldinspector;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;

/**
 *
 * @author matthias
 */
public class SheepColorPanel extends AbstractFilteredPanel<MCColor> {
    private Set<MCColor> colors = Collections.EMPTY_SET;

    public SheepColorPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        colors = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        colors = world.getSheepColors();
        buildListModel();
    }

    @Override
    protected List<MCColor> filteredList(String filter) {
        try {
            MCColor color = MCColor.fromByte(Byte.parseByte(filter));
            if(color != null)
                return Collections.singletonList(color);
        } catch(NumberFormatException ex) {}
        return colors.stream()
                .filter(e ->filter.isEmpty() || e.toString().contains(filter))
                .collect(Collectors.toList());
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<MCColor> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<MCColor> colors;

        public Highlighter(List<MCColor> colors) {
            this.colors = colors;
        }

        public List<MCColor> getEntities() {
            return colors;
        }

        private boolean filterColor(NBTTagCompound e) {
            return colors.contains(MCColor.fromByte(e.get("Color", Byte.class)));
        }

        private static final String MINECRAFT_SHEEP = "minecraft:sheep";

        @Override
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.getEntities(MINECRAFT_SHEEP)
                            .anyMatch(this::filterColor))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, WorldRenderer.HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = colors.stream()
                    .flatMap(chunk -> entry.chunk.getEntities(MINECRAFT_SHEEP))
                    .filter(this::filterColor)
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Sheep details for " + entry);
        }
    }
}
