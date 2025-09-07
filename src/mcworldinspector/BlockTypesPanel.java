package mcworldinspector;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.MapTreeModel;
import mcworldinspector.utils.RangeSlider;
import mcworldinspector.utils.StringHelpers;

/**
 *
 * @author matthias
 */
public class BlockTypesPanel extends AbstractFilteredPanel<String> {

    private final ExecutorService executorService;
    private final JCheckBox btnShowCount = new JCheckBox();
    private final RangeSlider subChunkSlider = new RangeSlider(0, 16);
    private final JLabel lowerLabel = new JLabel();
    private final JLabel upperLabel = new JLabel();
    private Set<String> blockTypes = Collections.emptySet();

    public BlockTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Blocks");

        btnShowCount.setText("Show block count (slower)");
        btnShowCount.addChangeListener(e -> doHighlighting());

        lowerLabel.setLabelFor(subChunkSlider);
        lowerLabel.setHorizontalAlignment(JLabel.RIGHT);
        upperLabel.setLabelFor(subChunkSlider);

        int labelWidth = lowerLabel.getFontMetrics(lowerLabel.getFont()).stringWidth("123");

        horizontal.addGroup(layout.createParallelGroup()
                .addComponent(btnShowCount)
                .addGroup(
                        layout.createSequentialGroup()
                                .addGap(4)
                                .addComponent(lowerLabel, labelWidth, labelWidth, labelWidth)
                                .addGap(4)
                                .addComponent(subChunkSlider)
                                .addGap(4)
                                .addComponent(upperLabel, labelWidth, labelWidth, labelWidth)
                                .addGap(4)));
        vertical.addGroup(layout.createSequentialGroup()
                .addComponent(btnShowCount)
                .addGap(4)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                        .addComponent(lowerLabel)
                        .addComponent(subChunkSlider)
                        .addComponent(upperLabel)));
        subChunkSlider.addPropertyChangeListener(e -> {
            updateLabels();
            doHighlighting();
        });
        updateLabels();
    }

    private void updateLabels() {
        lowerLabel.setText(Integer.toString(subChunkSlider.getLower() * 16));
        upperLabel.setText(Integer.toString(subChunkSlider.getUpper() * 16));
    }

    @Override
    public void reset() {
        blockTypes = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks()
                    .flatMap(Chunk::subChunks)
                    .flatMap(SubChunk::getBlockTypes)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            result.remove("minecraft:air");
            result.remove("minecraft:cave_air");
            result.remove("minecraft:bedrock");
            blockTypes = result;
            buildListModel();
        });
        subChunkSlider.setMin(world.is18() ? -4 : 0);
        subChunkSlider.setLower(subChunkSlider.getMin());
        subChunkSlider.setUpper(subChunkSlider.getMax());
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(blockTypes, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        final var lower = subChunkSlider.getLower();
        final var upper = subChunkSlider.getUpper();
        if (btnShowCount.isSelected()) {
            return world.getChunks().parallelStream()
                    .map(chunk -> {
                        final var count = chunk.subChunks(lower, upper)
                                .mapToLong(sc -> sc.countBlocks(selected))
                                .sum();
                        if (count > 0)
                            return new ChunkHighlightEntry.WithCount(chunk, count) {
                                @Override
                                public void showDetailsFor(Component parent) {
                                    showDetails(parent, this, lower, upper, selected);
                                }
                            };
                        return null;
                    }).filter(che -> che != null).sorted();
        }

        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.subChunks(lower, upper)
                .flatMap(SubChunk::getBlockTypes)
                .anyMatch(selected::contains))
                .map(chunk -> new ChunkHighlightEntry(chunk) {
            @Override
            public void showDetailsFor(Component parent) {
                showDetails(parent, this, lower, upper, selected);
            }
        });
    }

    private static void showDetails(Component parent, ChunkHighlightEntry entry, int lower, int upper, List<String> blockTypes) {
        final TreeMap<Integer, ArrayList<SubChunk.BlockInfo>> blocks = new TreeMap<>();
        final int x = entry.chunk.x << 4;
        final int z = entry.chunk.z << 4;
        entry.chunk.subChunks(lower, upper).flatMap(sc
                -> sc.findBlocks(blockTypes, new BlockPos(x, sc.getGlobalY(), z)))
                .forEach(b -> blocks.computeIfAbsent(b.y, k -> new ArrayList<>()).add(b));
        final var highestCount = StringHelpers.widthForCount(blocks.values().stream().mapToInt(v -> v.size()).max());
        final MapTreeModel<Integer, SubChunk.BlockInfo> model = new MapTreeModel<>(blocks,
                e -> String.format("Y=%3d  %s", e.getKey(), StringHelpers.formatCount(e.getValue().size(), "Block", highestCount)));
        final JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setFont(new Font( "Monospaced", Font.PLAIN, tree.getFont().getSize()));
        if (model.getChildCount(model.getRoot()) == 1)
            tree.expandRow(0);
        final JScrollPane sp = new JScrollPane(tree);
        final JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent),
                "Block details for " + entry, Dialog.ModalityType.DOCUMENT_MODAL);
        final JButton btnOk = new JButton(new AbstractAction("Ok") {
            @Override
            public void actionPerformed(ActionEvent e) {
                dlg.dispose();
            }
        });
        final GroupLayout layout = new GroupLayout(dlg.getContentPane());
        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup()
                        .addComponent(sp, 600, 1000, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(btnOk)))
                .addContainerGap());
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(sp, 400, 1000, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnOk)
                .addContainerGap());
        dlg.getContentPane().setLayout(layout);
        dlg.setLocationByPlatform(true);
        dlg.pack();
        dlg.setVisible(true);
    }
}
