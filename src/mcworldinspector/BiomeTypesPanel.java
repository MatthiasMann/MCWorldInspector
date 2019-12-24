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
public class BiomeTypesPanel extends JPanel {
    private final JTextField filterTF = new JTextField();
    private final JList<Chunk.Biome> biomeList = new JList<>();
    private Set<Chunk.Biome> biomes = Collections.EMPTY_SET;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public BiomeTypesPanel(Supplier<WorldRenderer> renderer) {
        super(null);

        biomeList.addListSelectionListener((e) -> {
            final WorldRenderer r = renderer.get();
            if(r != null) {
                final ListModel<Chunk.Biome> model = biomeList.getModel();
                r.highlight(new Highlighter(biomeList.getSelectedValuesList()));
            }
        });
        filterTF.getDocument().addDocumentListener(new DocumentChangedListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                buildEntityListModel();
            }
        });
        
        JScrollPane blockListSP = new JScrollPane(biomeList);
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

    public void setBiomes(Set<Chunk.Biome> biomes) {
        this.biomes = biomes;
        buildEntityListModel();
    }

    private void buildEntityListModel() {
        String filter = filterTF.getText();
        final List<Chunk.Biome> filtered = biomes.stream().filter(e ->
            filter.isEmpty() || e.name.contains(filter)).collect(Collectors.toList());
        biomeList.setModel(new SimpleListModel<>(filtered));
    }
    
    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        private final List<Chunk.Biome> biomes;

        public Highlighter(List<Chunk.Biome> biomes) {
            this.biomes = biomes;
        }

        public List<Chunk.Biome> getBiomes() {
            return biomes;
        }

        @Override
        public List<WorldRenderer.HighlightEntry> apply(World world) {
            return world.getChunks().parallelStream()
                    .filter(chunk -> chunk.biomes().anyMatch(biomes::contains))
                    .map(chunk -> new WorldRenderer.HighlightEntry(chunk))
                    .collect(Collectors.toList());
        }
    }
}
