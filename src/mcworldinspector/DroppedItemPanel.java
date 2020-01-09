package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public class DroppedItemPanel extends AbstractFilteredPanel<String> {
    public static final String MINECRAFT_ITEM = "minecraft:item";

    private final ExecutorService executorService;
    private Set<String> items = Collections.emptySet();

    public DroppedItemPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Dropped items");
    }

    @Override
    public void reset() {
        items = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks()
                    .flatMap(chunk -> chunk.getEntities(MINECRAFT_ITEM))
                    .map(DroppedItemPanel::getItemID)
                    .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            items = result;
            buildListModel();
        });
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(items, filter);
    }

    private static String getItemID(NBTTagCompound e) {
        return e.getCompound("Item").getString("id");
    }

    private static boolean isDroppedItem(NBTTagCompound e, List<String> selected) {
        return MINECRAFT_ITEM.equals(e.getString("id")) &&
                selected.contains(getItemID(e));
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        return EntityTypesPanel.createHighlighter(world,
                e -> isDroppedItem(e, selected), "Dropped item details for ");
    }
}
