package mcworldinspector;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.GroupLayout;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import mcworldinspector.utils.DocumentChangedListener;
import mcworldinspector.utils.SimpleListModel;

/**
 *
 * @author matthias
 */
public abstract class AbstractFilteredPanel<T> extends JPanel {
    private final JTextField filterTF = new JTextField();
    private final JList<T> list = new JList<>();
    
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public AbstractFilteredPanel(Supplier<WorldRenderer> renderer) {
        super(null);

        list.addListSelectionListener((e) -> {
            final WorldRenderer r = renderer.get();
            if(r != null)
                r.highlight(createHighlighter(list.getSelectedValuesList()));
        });
        filterTF.getDocument().addDocumentListener(new DocumentChangedListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                buildListModel();
            }
        });
        
        JScrollPane blockListSP = new JScrollPane(list);
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

    protected void buildListModel() {
        String filter = filterTF.getText();
        list.setModel(new SimpleListModel<>(filteredList(filter)));
    }

    public abstract void reset();
    public abstract void setWorld(World world);

    protected abstract List<T> filteredList(String filter);
    protected abstract WorldRenderer.HighlightSelector createHighlighter(List<T> selected);
    
    protected static List<String> filteredStringList(Collection<String> c, String filter) {
        return c.stream().filter(e ->
            filter.isEmpty() || e.contains(filter)).collect(Collectors.toList());
    }
}
