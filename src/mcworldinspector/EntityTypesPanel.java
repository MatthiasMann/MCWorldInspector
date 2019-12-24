package mcworldinspector;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.GroupLayout;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.event.DocumentEvent;
import mcworldinspector.utils.DocumentChangedListener;
import mcworldinspector.utils.SimpleListModel;

/**
 *
 * @author matthias
 */
public class EntityTypesPanel extends JPanel {
    private final JTextField filterTF = new JTextField();
    private final JList<String> entityList = new JList<>();
    private Set<String> entities = Collections.EMPTY_SET;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public EntityTypesPanel(Supplier<WorldRenderer> renderer) {
        super(null);

        entityList.addListSelectionListener((e) -> {
            final WorldRenderer r = renderer.get();
            if(r != null) {
                final ListModel<String> model = entityList.getModel();
                r.highlight(new Highlighter(entityList.getSelectedValuesList()));
            }
        });
        filterTF.getDocument().addDocumentListener(new DocumentChangedListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                buildEntityListModel();
            }
        });
        
        JScrollPane blockListSP = new JScrollPane(entityList);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(filterTF, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(blockListSP, GroupLayout.DEFAULT_SIZE, 1000, Short.MAX_VALUE));
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(filterTF)
                        .addComponent(blockListSP));
    }

    public void setEntities(Set<String> entities) {
        this.entities = entities;
        buildEntityListModel();
    }

    private void buildEntityListModel() {
        String filter = filterTF.getText();
        final List<String> filtered = entities.stream().filter(e ->
            filter.isEmpty() || e.contains(filter)).collect(Collectors.toList());
        entityList.setModel(new SimpleListModel<>(filtered));
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
        public List<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.entities().anyMatch(entities::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk))
                    .collect(Collectors.toList());
        }
    }
}
