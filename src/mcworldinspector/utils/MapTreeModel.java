package mcworldinspector.utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author matthias
 */
public class MapTreeModel<K, V> implements TreeModel {

    static class Node<V> {
        final String key;
        final List<V> list;

        Node(String key, List<V> list) {
            this.key = key;
            this.list = list;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private final Node<V> root;

    public MapTreeModel(Map<K, ? extends List<V>> map, Function<K, String> toString) {
        this.root = new Node("", map.entrySet().stream()
                .map(e -> new Node<>(toString.apply(e.getKey()), e.getValue()))
                .collect(Collectors.toList()));
    }

    public MapTreeModel(Map<K, ? extends List<V>> map) {
        this(map, Objects::toString);
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if(parent instanceof Node) {
            return ((Node)parent).list.get(index);
        }
        throw new UnsupportedOperationException("Not a parent");
    }

    @Override
    public int getChildCount(Object parent) {
        if(parent instanceof Node) {
            return ((Node)parent).list.size();
        }
        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        if(node instanceof Node) {
            return ((Node)node).list.isEmpty();
        }
        return true;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if(parent instanceof Node) {
            return ((Node)parent).list.indexOf(child);
        }
        throw new UnsupportedOperationException("Not a parent");
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        // tree doesn't change
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
    }
}
