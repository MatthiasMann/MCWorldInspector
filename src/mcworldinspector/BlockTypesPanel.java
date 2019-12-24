package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public class BlockTypesPanel extends AbstractFilteredPanel<String> {
    private Set<String> blockTypes = Collections.EMPTY_SET;

    public BlockTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        blockTypes = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        blockTypes = world.getBlockTypes();
        buildListModel();
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(blockTypes, filter);
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<String> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<String> blockTypes;

        public Highlighter(List<String> blockTypes) {
            this.blockTypes = blockTypes;
        }

        public List<String> getBlockTypes() {
            return blockTypes;
        }

        @Override
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.getBlockTypes().anyMatch(blockTypes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }
    }
}
