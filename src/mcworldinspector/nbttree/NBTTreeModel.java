package mcworldinspector.nbttree;

import java.awt.Component;
import java.awt.Dimension;
import java.net.URL;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
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
public class NBTTreeModel extends DefaultTreeModel {

    public NBTTreeModel(NBTBase nbt) {
        super(makeRoot(nbt));
    }
    
    public static void displayNBT(Component parent, NBTBase nbt) {
        NBTTreeModel model = new NBTTreeModel(nbt);
        JTree tree = new JTree(model);
        tree.setCellRenderer(new NBTTreeModel.NBTTreeCellRenderer());
        JScrollPane pane = new JScrollPane(tree);
        pane.setMinimumSize(new Dimension(1000, 600));
        pane.setPreferredSize(new Dimension(1000, 600));
        JOptionPane.showMessageDialog(parent, pane);
    }

    private static DefaultMutableTreeNode makeRoot(NBTBase nbt) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            NodeWithIcon.determineIcon("", nbt));
        makeNode(root, nbt);
        return root;
    }

    private static void makeNode(DefaultMutableTreeNode parent, Object obj) {
        if(obj instanceof NBTTagCompound) {
            ((NBTTagCompound)obj).entrySet().stream()
                    .sorted((a,b) -> a.getKey().compareTo(b.getKey()))
                    .forEach(e -> {
                        DefaultMutableTreeNode child = new DefaultMutableTreeNode(
                                NodeWithIcon.determineIcon(e.getKey(), e.getValue()));
                        makeNode(child, e.getValue());
                        parent.insert(child, parent.getChildCount());
                    });
        } else if(obj instanceof NBTTagList) {
            NBTArray a = (NBTArray)obj;
            for(int idx=0 ; idx<a.size() ; idx++) {
                final Object value = a.get(idx);
                final DefaultMutableTreeNode child = new DefaultMutableTreeNode(
                        NodeWithIcon.determineIcon(Integer.toString(idx), value));
                makeNode(child, value);
                parent.insert(child, parent.getChildCount());
            }
        } else if(obj instanceof NBTArray) {
            ((NBTArray)obj).forEach(e -> makeNode(parent, e));
        } else {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(
                    NodeWithIcon.determineIcon(Objects.toString(obj), obj));
            parent.insert(child, parent.getChildCount());
        }
    }
    
    public static class NodeWithIcon {
        public final String text;
        public final URL icon;

        private static final HashMap<Class, URL> ICONS = new HashMap<>();
        static {
            final URL NBT_BYTE = NodeWithIcon.class.getResource("nbt_byte.png");
            final URL NBT_SHORT = NodeWithIcon.class.getResource("nbt_short.png");
            final URL NBT_INT = NodeWithIcon.class.getResource("nbt_int.png");
            final URL NBT_LONG = NodeWithIcon.class.getResource("nbt_long.png");
            final URL NBT_FLOAT = NodeWithIcon.class.getResource("nbt_float.png");
            final URL NBT_DOUBLE = NodeWithIcon.class.getResource("nbt_double.png");
            final URL NBT_LIST = NodeWithIcon.class.getResource("nbt_list.png");
            final URL NBT_COMPOUND = NodeWithIcon.class.getResource("nbt_compound.png");
            final URL NBT_BYTE_ARRAY = NodeWithIcon.class.getResource("nbt_byte_array.png");
            final URL NBT_INT_ARRAY = NodeWithIcon.class.getResource("nbt_int_array.png");
            final URL NBT_STRING = NodeWithIcon.class.getResource("nbt_string.png");
            
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

        public NodeWithIcon(String text, URL icon) {
            this.text = text;
            this.icon = icon;
        }
        
        public static Object determineIcon(String text, Object obj) {
            if(obj != null) {
                URL icon = ICONS.get(obj.getClass());
                if(icon != null)
                    return new NodeWithIcon(text, icon);
            }
            return text;
        }
    }
    
    public static class NBTTreeCellRenderer implements TreeCellRenderer {
        private final JLabel label = new JLabel();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (o instanceof NodeWithIcon) {
                NodeWithIcon node = (NodeWithIcon) o;
                label.setIcon(new ImageIcon(node.icon));
                label.setText(node.text);
            } else {
                label.setIcon(null);
                label.setText(Objects.toString(o));
            }
            return label;
        }
    }
}
