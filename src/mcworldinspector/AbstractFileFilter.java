package mcworldinspector;

/**
 *
 * @author matthias
 */
public abstract class AbstractFileFilter extends javax.swing.filechooser.FileFilter implements java.io.FileFilter {
    private final String description;

    public AbstractFileFilter(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
