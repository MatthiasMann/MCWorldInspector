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
public class MobSpawnerPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private Set<String> tileEntities = Collections.emptySet();

    public MobSpawnerPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Mob Spawners");
    }

    @Override
    public void reset() {
        tileEntities = Collections.emptySet();
        super.reset();
    }

    private static final String ID = "minecraft:mob_spawner";

    private static String getSpawnDataID(NBTTagCompound s) {
        final var sd = s.getCompound("SpawnData");
        final var entity = sd.getCompound("entity");
        return (entity != null ? entity : sd).getString("id");
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(c -> c.getTileEntities(ID))
                    .map(MobSpawnerPanel::getSpawnDataID)
                    .filter(Objects::nonNull)
                    .distinct()
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
                .filter(chunk -> chunk.getTileEntities(ID).map(MobSpawnerPanel::getSpawnDataID).anyMatch(selected::contains))
                .map(chunk -> new TileEntityTypesPanel.TileEntityHighlightEntry(chunk,
                        "Tile entity details for ",
                        t -> selected.contains(getSpawnDataID(t)),
                        e -> MCItem.createChestView(world, e, null)));
    }
}
