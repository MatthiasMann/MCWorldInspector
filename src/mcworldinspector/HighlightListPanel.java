package mcworldinspector;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractListModel;
import javax.swing.GroupLayout;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 *
 * @author matthias
 */
public class HighlightListPanel extends JPanel {
    private final JList<WorldRenderer.HighlightEntry> list = new JList<>();
    private WorldRenderer renderer;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public HighlightListPanel() {
        super(null);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener((e) -> {
            WorldRenderer.HighlightEntry value = list.getSelectedValue();
            if(renderer != null && value != null)
                renderer.scrollTo(value);
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2 && renderer != null) {
                    WorldRenderer.HighlightEntry value = list.getSelectedValue();
                    final Object highlightSelector = renderer.getHighlightSelector();
                    if(value != null && highlightSelector instanceof BlockTypesPanel.Highlighter) {
                        List<String> blockTypes =
                                ((BlockTypesPanel.Highlighter) highlightSelector).getBlockTypes();
                        final List<SubChunk.BlockPos> blocks = blockTypes.stream()
                                .flatMap(value.chunk::findBlocks).collect(Collectors.toList());
                        JList list = new JList(new AbstractListModel<SubChunk.BlockPos>() {
                            @Override
                            public int getSize() {
                                return blocks.size();
                            }
                            @Override
                            public SubChunk.BlockPos getElementAt(int index) {
                                return blocks.get(index);
                            }
                        });
                        // TODO: better UI
                        JOptionPane.showMessageDialog(HighlightListPanel.this, list,
                                "Block positions", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });

        JScrollPane listSP = new JScrollPane(list);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(listSP));
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(listSP));
    }

    public void setRenderer(WorldRenderer renderer) {
        this.renderer = renderer;
        if(renderer != null) {
            list.setModel(renderer.getHighlightsModel());
        } else {
            list.setModel(null);
        }
    }
}
