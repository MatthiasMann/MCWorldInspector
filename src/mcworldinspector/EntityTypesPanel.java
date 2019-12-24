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
public class EntityTypesPanel extends AbstractFilteredPanel<String> {
    private Set<String> entities = Collections.EMPTY_SET;

    public EntityTypesPanel(Supplier<WorldRenderer> renderer) {
        super(renderer);
    }

    @Override
    public void reset() {
        entities = Collections.EMPTY_SET;
        buildListModel();
    }

    @Override
    public void setWorld(World world) {
        entities = world.getEntityTypes();
        buildListModel();
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(entities, filter);
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
                    .filter(chunk -> chunk.entities().anyMatch(entities::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk));
        }
    }
}
