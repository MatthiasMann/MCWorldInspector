package mcworldinspector;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import mcworldinspector.utils.MapTreeModel;

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

        @Override
        public void showDetailsFor(Component parent, WorldRenderer.HighlightEntry entry) {
            final TreeMap<Integer, ArrayList<SubChunk.BlockInfo>> blocks = new TreeMap<>();
            blockTypes.stream().flatMap(entry.chunk::findBlocks).forEach(b -> {
                blocks.computeIfAbsent(b.y, ArrayList::new).add(b);
            });
            final MapTreeModel<Integer, SubChunk.BlockInfo> model = new MapTreeModel<>(blocks, y -> "Y=" + y);
            final JTree tree = new JTree(model);
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            if(model.getChildCount(model.getRoot()) == 1)
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
}
