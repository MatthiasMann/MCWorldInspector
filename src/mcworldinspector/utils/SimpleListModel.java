package mcworldinspector.utils;

import java.util.List;
import javax.swing.AbstractListModel;

/**
 *
 * @author matthias
 */
public class SimpleListModel<T> extends AbstractListModel<T> {
    
    private List<T> list;

    public SimpleListModel(List<T> list) {
        this.list = list;
    }

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public T getElementAt(int index) {
        return list.get(index);
    }

    public void setList(List<T> list) {
        int oldCount = this.list.size();
        this.list = list;
        int newCount = this.list.size();
        if(newCount > oldCount)
            super.fireIntervalAdded(this, oldCount, newCount - 1);
        else if(newCount < oldCount)
            super.fireIntervalRemoved(this, newCount, oldCount - 1);
        if(newCount > 0)
            super.fireContentsChanged(this, 0, newCount - 1);
    }

    public void fireIntervalRemoved(int first, int last) {
        super.fireIntervalRemoved(this, first, last);
    }

    public void fireIntervalAdded(int first, int last) {
        super.fireIntervalAdded(this, first, last);
    }
}
