package mcworldinspector;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.GroupLayout;
import javax.swing.JList;
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
        list.addListSelectionListener(e -> {
            WorldRenderer.HighlightEntry value = list.getSelectedValue();
            if(renderer != null && value != null)
                renderer.scrollTo(value);
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getClickCount());
            };
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
    
    private void handleClick(int clickCount) {
        if(clickCount == 2 && renderer != null) {
            WorldRenderer.HighlightEntry value = list.getSelectedValue();
            final WorldRenderer.HighlightSelector highlightSelector = renderer.getHighlightSelector();
            if(value != null && highlightSelector != null)
                highlightSelector.showDetailsFor(HighlightListPanel.this, value);
        }
    }

    public void selectFromRenderer(Point p, int clickCount) {
        if(renderer == null)
            return;
        final WorldRenderer.HighlightEntry selected = list.getSelectedValue();
        if(selected != null && selected.contains(p))
            handleClick(clickCount);
        else {
            final int idx = renderer.getHighlightsModel().findIndex(e -> e.contains(p));
            if(idx >= 0) {
                list.setSelectedIndex(idx);
                list.ensureIndexIsVisible(idx);
                handleClick(clickCount);
            }
        }
    }
}
