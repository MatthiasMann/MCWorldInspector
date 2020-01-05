package mcworldinspector.utils;

import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

/**
 *
 * @author matthias
 */
public class ContextMenuMouseListener extends MouseAdapter {

    private final Consumer<MouseEvent> showPopupMenu;

    public ContextMenuMouseListener(Consumer<MouseEvent> showPopupMenu) {
        this.showPopupMenu = showPopupMenu;
    }

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
        showPopupMenu.accept(e);
    }

    public static void install(JComponent c, Consumer<MouseEvent> showPopupMenu) {
        c.addMouseListener(new ContextMenuMouseListener(showPopupMenu));
    }

    public static void install(JComponent c, Function<MouseEvent, JPopupMenu> createPopupMenu) {
        c.addMouseListener(new ContextMenuMouseListener(e -> {
            final var popupMenu = createPopupMenu.apply(e);
            if(popupMenu != null && popupMenu.getComponentCount() > 0)
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
        }));
    }

    public @FunctionalInterface interface JTableConextMenuCreator {
        public JPopupMenu accept(MouseEvent e, int row, int column);
    }

    public static void install(JTable table, JTableConextMenuCreator showPopupMenu, boolean selectCell) {
        install(table, e -> {
            final Point point = e.getPoint();
            final int row = table.rowAtPoint(point);
            if(row < 0)
                return null;
            final int column = table.columnAtPoint(point);
            if(column < 0)
                return null;
            if(selectCell) {
                table.setRowSelectionInterval(row, row);
                table.setColumnSelectionInterval(column, column);
            }
            return showPopupMenu.accept(e, row, column);
        });
    }

    public static void copyToClipboard(String str) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection(str), null);
    }
}
