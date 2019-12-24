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
public class StructureTypesPanel extends AbstractFilteredPanel<String> {
    private Set<String> structureTypes = Collections.EMPTY_SET;

    public StructureTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        structureTypes = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        structureTypes = world.getStructureTypes();
        buildListModel();
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(structureTypes, filter);
    }

    @Override
    protected WorldRenderer.HighlightSelector createHighlighter(List<String> selected) {
        return new Highlighter(selected);
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<String> structureTypes;

        public Highlighter(List<String> structureTypes) {
            this.structureTypes = structureTypes;
        }

        public List<String> getStructureTypes() {
            return structureTypes;
        }

        @Override
        public Stream<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.structures().anyMatch(structureTypes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }
    }
}
