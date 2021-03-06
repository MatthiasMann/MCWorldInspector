package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class SheepColorPanel extends AbstractFilteredPanel<MCColor> {
    public static final String MINECRAFT_SHEEP = "minecraft:sheep";

    private final ExecutorService executorService;
    private Set<MCColor> colors = Collections.emptySet();

    public SheepColorPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Sheep");
    }

    @Override
    public void reset() {
        colors = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
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
            MCColor color = MCColor.fromNumber(Byte.parseByte(filter));
            if(color != null)
                return Collections.singletonList(color);
        } catch(NumberFormatException ex) {}
        return colors.stream()
                .filter(e ->filter.isEmpty() || e.toString().contains(filter))
                .collect(Collectors.toList());
    }

    private static boolean isSheepColor(NBTTagCompound e, List<MCColor> selected) {
        return MINECRAFT_SHEEP.equals(e.getString("id")) &&
                selected.contains(MCColor.fromNumber(e.get("Color", Number.class)));
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<MCColor> selected) {
        return EntityTypesPanel.createHighlighter(world,
                e -> isSheepColor(e, selected), "Sheep details for ");
    }
}
