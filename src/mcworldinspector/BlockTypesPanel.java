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
public class BlockTypesPanel extends JPanel {
    private final JTextField filterTF = new JTextField();
    private final JList<String> blockList = new JList<>();
    private Set<String> blockTypes = Collections.EMPTY_SET;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public BlockTypesPanel(Supplier<WorldRenderer> renderer) {
        super(null);

        blockList.addListSelectionListener((e) -> {
            final WorldRenderer r = renderer.get();
            if(r != null) {
                final ListModel<String> model = blockList.getModel();
                r.highlight(new Highlighter(blockList.getSelectedValuesList()));
            }
        });
        filterTF.getDocument().addDocumentListener(new DocumentChangedListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                buildBlockListModel();
            }
        });
        
        JScrollPane blockListSP = new JScrollPane(blockList);
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

    public void setBlockTypes(Set<String> blockTypes) {
        this.blockTypes = blockTypes;
        buildBlockListModel();
    }

    private void buildBlockListModel() {
        String filter = filterTF.getText();
        final List<String> filtered = blockTypes.stream().filter(e ->
            filter.isEmpty() || e.contains(filter)).collect(Collectors.toList());
        blockList.setModel(new SimpleListModel<>(filtered));
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
        public List<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.getBlockTypes().anyMatch(blockTypes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk))
                    .collect(Collectors.toList());
        }
    }
}
