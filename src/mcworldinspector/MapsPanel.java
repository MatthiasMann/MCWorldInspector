
package mcworldinspector;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import mcworldinspector.nbttree.NBTTreeModel;

/**
 *
 * @author matthias
 */
public class MapsPanel extends JPanel implements MCWorldInspector.InfoPanel {

    private final Supplier<WorldRenderer> renderer;
    private final Model model = new Model();

    public MapsPanel(Supplier<WorldRenderer> renderer) {
        this.renderer = renderer;
        initComponents();

        mapsTable.getSelectionModel().addListSelectionListener(l -> {
            final var selected = mapsTable.getSelectedRows();
            mapDisplay.setIcon((selected.length == 1) ? new ImageIcon(
                        model.maps.get(selected[0]).createImage()) : null);
            final var r = renderer.get();
            if(r == null)
                return;
            r.highlight(IntStream.of(selected)
                .mapToObj(model.maps::get)
                .flatMap(map -> {
                    final var x = map.getX();
                    final var z = map.getZ();
                    final var scale = map.getScale();
                    if(x == null || z == null || scale == null)
                        return Stream.empty();
                    return Stream.of(new MapHighlightEntry(map, x, z, scale));
                }));
        });
    }

    @Override
    public JComponent getTabComponent() {
        return this;
    }

    @Override
    public void reset() {
        model.maps = Collections.emptyList();
        model.fireTableDataChanged();
    }

    @Override
    public void setWorld(World world) {
        model.maps = new ArrayList<>(world.getMaps().values());
        model.fireTableDataChanged();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        mapsTable = new javax.swing.JTable();
        mapDisplay = new javax.swing.JLabel();

        mapsTable.setModel(model);
        jScrollPane1.setViewportView(mapsTable);

        mapDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        mapDisplay.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        mapDisplay.setMinimumSize(new java.awt.Dimension(128, 128));
        mapDisplay.setPreferredSize(new java.awt.Dimension(128, 128));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(mapDisplay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mapDisplay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel mapDisplay;
    private javax.swing.JTable mapsTable;
    // End of variables declaration//GEN-END:variables

    class Model extends AbstractTableModel {
        List<MCMap> maps = Collections.emptyList();

        @Override
        public int getRowCount() {
            return maps.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Byte.class : Integer.class;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return "X";
                case 1: return "Z";
                case 2: return "scale";
                case 3: return "index";
                default: throw new AssertionError();
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MCMap map = maps.get(rowIndex);
            switch (columnIndex) {
                case 0: return map.getX();
                case 1: return map.getZ();
                case 2: return map.getScale();
                case 3: return map.getIndex();
                default:
                    throw new AssertionError();
            }
        }
    }

    static class MapHighlightEntry implements WorldRenderer.HighlightEntry {
        final MCMap map;
        final int x;
        final int z;
        final int scale;

        public MapHighlightEntry(MCMap map, int x, int z, int scale) {
            this.map = map;
            this.x = x;
            this.z = z;
            this.scale = scale;
        }

        @Override
        public int getX() {
            return x - (64 << scale);
        }

        @Override
        public int getZ() {
            return z - (64 << scale);
        }

        @Override
        public int getWidth() {
            return 128 << scale;
        }

        @Override
        public int getHeight() {
            return 128 << scale;
        }

        @Override
        public String toString() {
            final int areaX = getX();
            final int areaZ = getZ();
            return "Map " + map.getIndex() + " for <" + areaX+ ", " + areaZ +
                    "> to <" + (areaX + getWidth()) + ", " + (areaZ + getHeight()) + '>';
        }

        @Override
        public void showDetailsFor(Component parent) {
            NBTTreeModel.displayNBT(parent, map.getNbt(), "Map " + map.getIndex());
        }
    }
}
