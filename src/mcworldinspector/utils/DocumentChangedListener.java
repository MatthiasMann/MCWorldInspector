package mcworldinspector.utils;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 *
 * @author matthias
 */
public abstract class DocumentChangedListener implements DocumentListener {
    public abstract void documentChanged(DocumentEvent e);

    @Override
    public final void insertUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public final void removeUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public final void changedUpdate(DocumentEvent e) {
        documentChanged(e);
    }
}
