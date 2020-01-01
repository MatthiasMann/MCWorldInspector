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
public class SheepColorPanel extends AbstractFilteredPanel<MCColor> {
    public static final String MINECRAFT_SHEEP = "minecraft:sheep";

    private final ExecutorService executorService;
    private Set<MCColor> colors = Collections.emptySet();

    public SheepColorPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        colors = Collections.emptySet();
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks()
                    .flatMap(chunk -> chunk.getEntities(MINECRAFT_SHEEP))
                    .flatMap(v -> MCColor.asStream(v.get("Color", Byte.class)))
                .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            colors = result;
            buildListModel();
        });
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

        @Override
        public Stream<HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.getEntities(MINECRAFT_SHEEP)
                            .anyMatch(this::filterColor))
                    .map(chunk -> new HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = entry.chunk
                    .getEntities(MINECRAFT_SHEEP).filter(this::filterColor)
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Sheep details for " + entry);
        }
    }
}
