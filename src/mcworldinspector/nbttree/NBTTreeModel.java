package mcworldinspector.nbttree;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
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

    public static void displayNBT(Component parent, NBTBase nbt, String title) {
        NBTTreeModel model = new NBTTreeModel(nbt);
        JTreeTable tree = new JTreeTable(model);
        tree.getTree().setRootVisible(false);
        tree.getTree().setShowsRootHandles(true);
        tree.setCellRenderer(new NBTTreeCellRenderer());
        tree.setDefaultRenderer(TextWithIcon.class, new TextWithIconRenderer());
        if(model.getChildCount(model.getRoot()) == 1)
            tree.getTree().expandRow(0);
        addContextMenu(tree);
        JScrollPane pane = new JScrollPane(tree);
        pane.setMinimumSize(new Dimension(1000, 600));
        pane.setPreferredSize(new Dimension(1000, 600));
        JOptionPane.showMessageDialog(parent, pane, title, JOptionPane.PLAIN_MESSAGE);
    }

    public static void addContextMenu(JTreeTable treeTable) {
        assert(treeTable.getModel() instanceof NBTTreeModel);
        treeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                checkPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                checkPopup(e);
            }
            
            public void checkPopup(MouseEvent e) {
                if(!e.isPopupTrigger())
                    return;
                final Point point = e.getPoint();
                final int row = treeTable.rowAtPoint(point);
                if(row < 0)
                    return;
                final int column = treeTable.columnAtPoint(point);
                treeTable.setRowSelectionInterval(row, row);
                treeTable.setColumnSelectionInterval(column, column);
                final Node node = (Node)treeTable.getTree().getLastSelectedPathComponent();
                if(node == null)
                    return;
                final boolean hasValue = !node.value.text.isEmpty();
                JPopupMenu popupMenu = new JPopupMenu();
                if(column == 1 && hasValue)
                    popupMenu.add(new AbstractAction("Copy value") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            copyToClipboard(node.value.text);
                        }
                    });
                if(hasValue)
                    popupMenu.add(new CopyAsJSONAction("Copy row as JSON", node, false));
                if(!node.children.isEmpty())
                    popupMenu.add(new CopyAsJSONAction("Copy tree as JSON", node, true));
                if(popupMenu.getComponentCount() > 0)
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private static void copyToClipboard(String str) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection(str), null);
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
                copyToClipboard(sw.toString());
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

    private static Node makeRoot(NBTBase nbt) {
        Node root = new Node("", Node.iconForObject(nbt));
        makeNode(root, nbt);
        return root;
    }

    private static void makeNode(Node parent, Object obj) {
        if(obj instanceof NBTTagCompound) {
            ((NBTTagCompound)obj).entrySet().stream()
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
                makeNode(node, value);
                parent.addChild(node);
            }
        } else {
            parent.value = new TextWithIcon(Objects.toString(obj));
        }
    }
    
    private static final String POS_FORMAT_INT = "<%d, %d, %d>";
    private static final String POS_FORMAT_DOUBLE = "<%.1f, %.1f, %.1f>";

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
                node.value = new TextWithIcon(String.format(POS_FORMAT_DOUBLE, pos.get(0), pos.get(1), pos.get(2)));
                return;
            }
        }
        if(value instanceof NBTIntArray) {
            NBTIntArray pos = (NBTIntArray)value;
            if(pos.size() == 3) {
                node.value = new TextWithIcon(String.format(POS_FORMAT_INT, pos.get(0), pos.get(1), pos.get(2)));
                return;
            }
        }
        if(value instanceof NBTTagCompound) {
            NBTTagCompound nbt = (NBTTagCompound)value;
            if(nbt.size() == 3) {
                Object x = nbt.get("x");
                Object y = nbt.get("y");
                Object z = nbt.get("z");
                if(x instanceof Integer && y instanceof Integer && z instanceof Integer) {
                    node.value = new TextWithIcon(String.format(POS_FORMAT_INT, x, y, z));
                    return;
                }
                if((x instanceof Double && y instanceof Double && z instanceof Double) ||
                        (x instanceof Float && y instanceof Float && z instanceof Float)) {
                    node.value = new TextWithIcon(String.format(POS_FORMAT_DOUBLE, x, y, z));
                    return;
                }
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
