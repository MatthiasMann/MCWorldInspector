package mcworldinspector.utils;

import java.awt.Component;
import java.awt.Window;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 *
 * @author matthias
 */
public class JDialog extends javax.swing.JDialog {

    public JDialog(Component parent, String title, boolean modal) {
        super(getWindowForDialog(parent), title, modal
            ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
    }

    public JDialog(Component parent, boolean modal) {
        this(parent, "", modal);
    }

    public static Window getWindowForDialog(Component parent) {
        if(parent != null) {
            Window window = SwingUtilities.getWindowAncestor(parent);
            if(window != null)
                return window;
        }
        return JOptionPane.getRootFrame();
    }
}
