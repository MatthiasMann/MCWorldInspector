package mcworldinspector.nbttree;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import mcworldinspector.MCColor;
import mcworldinspector.jtreetable.AbstractTreeTableModel;
import mcworldinspector.jtreetable.JTreeTable;
import mcworldinspector.jtreetable.TreeTableModel;
import mcworldinspector.nbt.NBTArray;
import mcworldinspector.nbt.NBTBase;
import mcworldinspector.nbt.NBTByteArray;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTFloatArray;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTLongArray;
import mcworldinspector.nbt.NBTShortArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.utils.ContextMenuMouseListener;
import org.json.JSONException;
import org.json.JSONWriter;

/**
 *
 * @author matthias
 */
public class NBTTreeModel extends AbstractTreeTableModel {

    public NBTTreeModel(NBTBase nbt) {
        super(makeRoot(nbt));
    }

    public<T extends NBTBase> NBTTreeModel(List<Map.Entry<String, T>> nbtList) {
        super(makeRoot(nbtList));
    }

    public static JTreeTable createNBTreeTable(NBTTreeModel model) {
        JTreeTable tree = new JTreeTable(model);
        tree.getTree().setRootVisible(false);
        tree.getTree().setShowsRootHandles(true);
        tree.setCellRenderer(new NBTTreeCellRenderer());
        tree.setDefaultRenderer(TextWithIcon.class, new TextWithIconRenderer());
        if(model.getChildCount(model.getRoot()) == 1)
            tree.getTree().expandRow(0);
        addContextMenu(tree);
        return tree;
    }

    public static JScrollPane wrapInScrollPane(JComponent comb) {
        JScrollPane pane = new JScrollPane(comb);
        pane.setMinimumSize(new Dimension(1000, 600));
        pane.setPreferredSize(new Dimension(1000, 600));
        return pane;
    }

    public static JScrollPane wrapInScrollPane(JComponent comb, String name) {
        final var pane = wrapInScrollPane(comb);
        pane.setName(name);
        return pane;
    }

    public static void displayNBT(Component parent, NBTBase nbt, String title) {
        displayNBT(parent, new NBTTreeModel(nbt), title);
    }

    public static void displayNBT(Component parent, NBTTreeModel model, String title) {
        JOptionPane.showMessageDialog(parent,
                wrapInScrollPane(createNBTreeTable(model)),
                title, JOptionPane.PLAIN_MESSAGE);
    }

    public static void displayNBT(Component parent, NBTTreeModel model, String title,
            Collection<? extends JComponent> tabs) {
        final var tabbedPane = new JTabbedPane();
        tabbedPane.add("NBT", wrapInScrollPane(createNBTreeTable(model)));
        tabs.forEach(tabbedPane::add);
        JOptionPane.showMessageDialog(parent, tabbedPane, title, JOptionPane.PLAIN_MESSAGE);
    }

    public static<T extends NBTTagCompound> void displayNBT(Component parent,
            List<Map.Entry<String, T>> list, String title,
            Function<Map.Entry<String, T>, Stream<? extends JComponent>> createTabs) {
        final var nbtTreeModel = new NBTTreeModel(list);
        final List<? extends JComponent> tabs = (createTabs == null)
                ? Collections.emptyList() : list.stream()
                        .flatMap(createTabs).collect(Collectors.toList());
        if(tabs.isEmpty())
            displayNBT(parent, nbtTreeModel, title);
        else
            displayNBT(parent, nbtTreeModel, title, tabs);
    }

    public static void addContextMenu(JTreeTable treeTable) {
        assert(treeTable.getModel() instanceof NBTTreeModel);
        ContextMenuMouseListener.install(treeTable, (e, row, column) -> {
            final Node node = (Node)treeTable.getTree().getLastSelectedPathComponent();
            if(node == null)
                return null;
            final boolean hasValue = !node.value.text.isEmpty();
            JPopupMenu popupMenu = new JPopupMenu();
            if(column == 1 && hasValue)
                popupMenu.add(new AbstractAction("Copy value") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ContextMenuMouseListener.copyToClipboard(node.value.text);
                    }
                });
            if(hasValue)
                popupMenu.add(new CopyAsJSONAction("Copy row as JSON", node, false));
            if(!node.children.isEmpty())
                popupMenu.add(new CopyAsJSONAction("Copy tree as JSON", node, true));
            return popupMenu;
        }, true);
    }

    static class CopyAsJSONAction extends AbstractAction {
        private final Node node;
        private final boolean withChildren;

        public CopyAsJSONAction(String name, Node node, boolean withChildren) {
            super(name);
            this.node = node;
            this.withChildren = withChildren;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                StringWriter sw = new StringWriter();
                node.writeJSON(new JSONWriter(sw), withChildren);
                ContextMenuMouseListener.copyToClipboard(sw.toString());
            } catch (JSONException ex) {
                Logger.getLogger(CopyAsJSONAction.class.getName())
                        .log(Level.SEVERE, "Unable to create JSON", ex);
            }
        }
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0: return "Node";
            case 1: return "Value";
            default:
                throw new AssertionError();
        }
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0: return TreeTableModel.class;
            case 1: return TextWithIcon.class;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public Object getValueAt(Object node, int column) {
        switch (column) {
            case 0: return node;
            case 1: return ((Node)node).value;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public Node getRoot() {
        return (Node)super.getRoot();
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((Node)parent).children.get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((Node)parent).children.size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((Node)node).children.isEmpty();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return ((Node)parent).children.indexOf(child);
    }

    public static TreePath getPathForLastChild(TreePath path) {
        final var children = ((Node)path.getLastPathComponent()).children;
        final var size = children.size();
        return size == 0 ? null : path.pathByAddingChild(children.get(size- 1));
    }

    private static Node makeRoot(NBTBase nbt) {
        Node root = new Node("", Node.iconForObject(nbt));
        makeNode(root, nbt);
        return root;
    }

    private static<T extends NBTBase> Node makeRoot(List<Map.Entry<String, T>> nbtList) {
        final var root = new Node("", null);
        for(var e : nbtList) {
            final var nbt = e.getValue();
            final var child = new Node(e.getKey(), Node.iconForObject(nbt));
            makeNode(child, nbt);
            root.addChild(child);
        }
        return root;
    }

    private static void makeNode(Node parent, Object obj) {
        if(obj instanceof NBTTagCompound) {
            ((NBTTagCompound)obj).entries()
                    .sorted((a,b) -> a.getKey().compareTo(b.getKey()))
                    .forEach(e -> {
                        final Object value = e.getValue();
                        final Node node = new Node(e.getKey(), Node.iconForObject(value));
                        makeChildNodes(e.getKey(), node, value);
                        parent.addChild(node);
                    });
        } else if(obj instanceof NBTArray) {
            NBTArray a = (NBTArray)obj;
            for(int idx=0 ; idx<a.size() ; idx++) {
                final Object value = a.get(idx);
                final Node node = new Node(Integer.toString(idx), Node.iconForObject(value));
                makeChildNodes(parent.label, node, value);
                parent.addChild(node);
            }
        } else {
            parent.value = new TextWithIcon(Objects.toString(obj));
        }
    }
    
    private static final String POS_FORMAT_INT = "<%d, %d, %d>";
    private static final String POS_FORMAT_DOUBLE = "<%.1f, %.1f, %.1f>";

    public static String formatPosition(NBTDoubleArray pos) {
        return String.format(POS_FORMAT_DOUBLE, pos.get(0), pos.get(1), pos.get(2));
    }

    public static String formatPosition(NBTIntArray pos) {
        return String.format(POS_FORMAT_INT, pos.get(0), pos.get(1), pos.get(2));
    }

    public static String formatPosition(NBTTagCompound nbt) {
        String pos = formatPosition(nbt.get("x"), nbt.get("y"), nbt.get("z"));
        if(pos.isEmpty())
            pos = formatPosition(nbt.get("X"), nbt.get("Y"), nbt.get("Z"));
        return pos;
    }

    public static String formatPosition(Object x, Object y, Object z) {
        if(x instanceof Integer && y instanceof Integer && z instanceof Integer)
            return String.format(POS_FORMAT_INT, x, y, z);
        if((x instanceof Double && y instanceof Double && z instanceof Double) ||
                (x instanceof Float && y instanceof Float && z instanceof Float))
            return String.format(POS_FORMAT_DOUBLE, x, y, z);
        return "";
    }

    private static final String BB_FORMAT_INT = "<%d, %d, %d> to <%d, %d, %d>";

    public static String formatBoundingBox(NBTIntArray pos) {
        return String.format(BB_FORMAT_INT,
                pos.get(0), pos.get(1), pos.get(2),
                pos.get(3), pos.get(4), pos.get(5));
    }

    private static void makeChildNodes(String name, Node node, Object value) {
        MCColor color;
        if("Color".equalsIgnoreCase(name) && value instanceof Byte &&
                (color = MCColor.fromByte((Byte)value)) != null) {
            node.value = new TextWithIcon(color.toString(), getColorIcon(color));
            return;
        }
        if(value instanceof NBTDoubleArray) {
            NBTDoubleArray pos = (NBTDoubleArray)value;
            if(pos.size() == 3) {
                node.value = new TextWithIcon(formatPosition(pos));
                return;
            }
        }
        if(value instanceof NBTIntArray) {
            NBTIntArray pos = (NBTIntArray)value;
            if(pos.size() == 3) {
                node.value = new TextWithIcon(formatPosition(pos));
                return;
            }
            if(pos.size() == 6 && ("BB".equals(name) || "Entrances".equals(name))) {
                node.value = new TextWithIcon(formatBoundingBox(pos));
                return;
            }
        }
        if(value instanceof NBTTagCompound) {
            NBTTagCompound nbt = (NBTTagCompound)value;
            if(nbt.size() == 3) {
                String posStr = formatPosition(nbt);
                if(!posStr.isEmpty())
                    node.value = new TextWithIcon(posStr);
            }
        }
        makeNode(node, value);
    }
    
    private static final EnumMap<MCColor, Icon> COLOR_ICONS = new EnumMap<>(MCColor.class);

    private static Icon getColorIcon(MCColor color) {
        return COLOR_ICONS.computeIfAbsent(color, col -> {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(col.getColor());
                    g.fillRect(x, y, getIconWidth(), getIconHeight());
                }

                @Override
                public int getIconWidth() {
                    return 16;
                }

                @Override
                public int getIconHeight() {
                    return 16;
                }
            };
        });
    }

    public static class TextWithIcon {
        public static final TextWithIcon EMPTY = new TextWithIcon("");

        public final String text;
        public final Icon icon;

        public TextWithIcon(String text) {
            this.text = text;
            this.icon = null;
        }

        public TextWithIcon(String text, Icon icon) {
            this.text = text;
            this.icon = icon;
        }
    }

    public static class Node {
        private List<Node> children = Collections.emptyList();
        final String label;
        TextWithIcon value;
        Icon icon;

        private static ImageIcon getIcon(String name) {
            final URL url = Node.class.getResource(name);
            return (url != null) ? new ImageIcon(url) : null;
        }

        private static final HashMap<Class, ImageIcon> ICONS = new HashMap<>();
        static {
            final ImageIcon NBT_BYTE = getIcon("nbt_byte.png");
            final ImageIcon NBT_SHORT = getIcon("nbt_short.png");
            final ImageIcon NBT_INT = getIcon("nbt_int.png");
            final ImageIcon NBT_LONG = getIcon("nbt_long.png");
            final ImageIcon NBT_FLOAT = getIcon("nbt_float.png");
            final ImageIcon NBT_DOUBLE = getIcon("nbt_double.png");
            final ImageIcon NBT_LIST = getIcon("nbt_list.png");
            final ImageIcon NBT_COMPOUND = getIcon("nbt_compound.png");
            final ImageIcon NBT_BYTE_ARRAY = getIcon("nbt_byte_array.png");
            final ImageIcon NBT_INT_ARRAY = getIcon("nbt_int_array.png");
            final ImageIcon NBT_STRING = getIcon("nbt_string.png");
            
            ICONS.put(Byte.class, NBT_BYTE);
            ICONS.put(Short.class, NBT_SHORT);
            ICONS.put(Integer.class, NBT_INT);
            ICONS.put(Long.class, NBT_LONG);
            ICONS.put(Float.class, NBT_FLOAT);
            ICONS.put(Double.class, NBT_DOUBLE);
            ICONS.put(NBTByteArray.class, NBT_BYTE_ARRAY);
            ICONS.put(NBTShortArray.class, NBT_INT_ARRAY);
            ICONS.put(NBTIntArray.class, NBT_INT_ARRAY);
            ICONS.put(NBTLongArray.class, NBT_INT_ARRAY);
            ICONS.put(NBTFloatArray.class, NBT_INT_ARRAY);
            ICONS.put(NBTDoubleArray.class, NBT_INT_ARRAY);
            ICONS.put(NBTTagList.class, NBT_LIST);
            ICONS.put(String.class, NBT_STRING);
            ICONS.put(NBTTagCompound.Empty.class, NBT_COMPOUND);
            ICONS.put(NBTTagCompound.Single.class, NBT_COMPOUND);
            ICONS.put(NBTTagCompound.Small.class, NBT_COMPOUND);
            ICONS.put(NBTTagCompound.Large.class, NBT_COMPOUND);
        }

        Node(String label, Icon icon) {
            this.label = label;
            this.value = TextWithIcon.EMPTY;
            this.icon = icon;
        }

        static ImageIcon iconForObject(Object obj) {
            return (obj != null) ? ICONS.get(obj.getClass()) : null;
        }

        public void addChild(Node child) {
            if(children.isEmpty())
                children = new ArrayList<>();
            children.add(child);
        }

        public void writeJSON(JSONWriter w, boolean withChildren) throws JSONException {
            w.object()
                    .key("label").value(label)
                    .key("value").value(value.text);
            if(withChildren && !children.isEmpty()) {
                w.key("children").array();
                for(Node child : children)
                    child.writeJSON(w, true);
                w.endArray();
            }
            w.endObject();
        }

        public Optional<Node> findChild(String label) {
            return children.stream()
                    .filter(n -> n.label.equals(label))
                    .findFirst();
        }

        public int size() {
            return children.size();
        }

        public Node getChild(int index) {
            return children.get(index);
        }
    }
    
    public static class NBTTreeCellRenderer extends JLabel implements TreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Node n = (Node)value;
            setIcon(n.icon);
            setText(n.label);
            return this;
        }
    }
    
    public static class TextWithIconRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            TextWithIcon t = (TextWithIcon)value;
            setIcon(t.icon);
            setText(t.text);
        }
    }
}
