package mcworldinspector;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
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
public abstract class AbstractFilteredPanel<T> extends JPanel implements MCWorldInspector.InfoPanel {
    private final JTextField filterTF = new JTextField();
    private final JList<T> list = new JList<>();
    protected World world;
    protected WorldRenderer renderer;
    protected final GroupLayout layout;
    protected final GroupLayout.ParallelGroup horizontal;
    protected final GroupLayout.SequentialGroup vertical;
    
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public AbstractFilteredPanel() {
        super(null);

        list.addListSelectionListener(e -> doHighlighting());
        filterTF.getDocument().addDocumentListener(new DocumentChangedListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                buildListModel();
            }
        });

        JScrollPane blockListSP = new JScrollPane(list);
        layout = new GroupLayout(this);
        vertical = layout.createSequentialGroup()
                .addComponent(filterTF, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blockListSP, GroupLayout.DEFAULT_SIZE, 1000, Short.MAX_VALUE);
        horizontal = layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(filterTF)
                .addComponent(blockListSP);
        layout.setHorizontalGroup(horizontal);
        layout.setVerticalGroup(vertical);
        setLayout(layout);
    }

    protected void doHighlighting() {
        if(renderer != null) {
            final List<T> selected = list.getSelectedValuesList();
            renderer.highlight(selected.isEmpty() ?
                    Stream.empty() : createHighlighter(selected));
        }
    }

    protected void buildListModel() {
        String filter = filterTF.getText();
        list.setModel(new SimpleListModel<>(filteredList(filter)));
    }

    @Override
    public final JComponent getTabComponent() {
        return this;
    }

    @Override
    public void reset() {
        world = null;
        renderer = null;
        buildListModel();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        this.world = world;
        this.renderer = renderer;
    }

    protected abstract List<T> filteredList(String filter);
    protected abstract Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<T> selected);
    
    protected static List<String> filteredStringList(Collection<String> c, String filter) {
        return c.stream().filter(e ->
            filter.isEmpty() || e.contains(filter)).collect(Collectors.toList());
    }
}
