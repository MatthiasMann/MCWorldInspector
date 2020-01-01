package mcworldinspector;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public class VillagerPanel extends AbstractFilteredPanel<String> {
    public static final String MINECRAFT_VILLAGER = "minecraft:villager";

    private final ExecutorService executorService;
    private Set<String> professions = Collections.emptySet();

    public VillagerPanel(Supplier<WorldRenderer> renderer, ExecutorService executorService) {
        super(renderer);
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        professions = Collections.emptySet();
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks()
                    .flatMap(chunk -> chunk.getEntities(MINECRAFT_VILLAGER))
                    .map(VillagerPanel::getProfession)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            professions = result;
            buildListModel();
        });
    }

    static String getProfession(NBTTagCompound villager) {
        return villager.getCompound("VillagerData").getString("profession");
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(professions, filter);
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<String> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<String> professions;

        public Highlighter(List<String> professions) {
            this.professions = professions;
        }

        private boolean filter(NBTTagCompound villager) {
            return professions.contains(getProfession(villager));
        }

        @Override
        public Stream<HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.getEntities(MINECRAFT_VILLAGER)
                            .anyMatch(this::filter))
                    .map(chunk -> new HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = entry.chunk
                    .getEntities(MINECRAFT_VILLAGER).filter(this::filter)
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, "Villager details for " + entry);
        }
    }
}
