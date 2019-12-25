package mcworldinspector.nbttree;

import java.awt.Component;
import java.awt.Dimension;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;
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
        tree.setCellRenderer(new NBTTreeCellRenderer());
        JScrollPane pane = new JScrollPane(tree);
        pane.setMinimumSize(new Dimension(1000, 600));
        pane.setPreferredSize(new Dimension(1000, 600));
        JOptionPane.showMessageDialog(parent, pane, title, JOptionPane.PLAIN_MESSAGE);
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
            case 1: return String.class;
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
                        final Node node = new Node(e.getKey(), Node.iconForObject(e.getValue()));
                        makeNode(node, e.getValue());
                        parent.children.add(node);
                    });
        } else if(obj instanceof NBTArray) {
            NBTArray a = (NBTArray)obj;
            for(int idx=0 ; idx<a.size() ; idx++) {
                final Object value = a.get(idx);
                final Node node = new Node(Integer.toString(idx), Node.iconForObject(value));
                makeNode(node, value);
                parent.children.add(node);
            }
        } else {
            parent.value = Objects.toString(obj);
        }
    }
    
    public static class Node {
        final ArrayList<Node> children = new ArrayList<>();
        final String label;
        String value;
        URL icon;

        private static final HashMap<Class, URL> ICONS = new HashMap<>();
        static {
            final URL NBT_BYTE = Node.class.getResource("nbt_byte.png");
            final URL NBT_SHORT = Node.class.getResource("nbt_short.png");
            final URL NBT_INT = Node.class.getResource("nbt_int.png");
            final URL NBT_LONG = Node.class.getResource("nbt_long.png");
            final URL NBT_FLOAT = Node.class.getResource("nbt_float.png");
            final URL NBT_DOUBLE = Node.class.getResource("nbt_double.png");
            final URL NBT_LIST = Node.class.getResource("nbt_list.png");
            final URL NBT_COMPOUND = Node.class.getResource("nbt_compound.png");
            final URL NBT_BYTE_ARRAY = Node.class.getResource("nbt_byte_array.png");
            final URL NBT_INT_ARRAY = Node.class.getResource("nbt_int_array.png");
            final URL NBT_STRING = Node.class.getResource("nbt_string.png");
            
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
            ICONS.put(NBTTagCompound.class, NBT_COMPOUND);
        }

        Node(String label, URL icon) {
            this.label = label;
            this.value = "";
            this.icon = icon;
        }

        static URL iconForObject(Object obj) {
            return (obj != null) ? ICONS.get(obj.getClass()) : null;
        }
    }
    
    public static class NBTTreeCellRenderer implements TreeCellRenderer {
        private final JLabel label = new JLabel();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Node n = (Node)value;
            label.setIcon(n.icon != null ? new ImageIcon(n.icon) : null);
            label.setText(n.label);
            return label;
        }
    }
}
