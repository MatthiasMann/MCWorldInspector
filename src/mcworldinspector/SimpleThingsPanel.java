package mcworldinspector;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.ContextMenuMouseListener;

/**
 *
 * @author matthias
 */
public class SimpleThingsPanel extends JPanel implements MCWorldInspector.InfoPanel {
    private final ExecutorService executorService;
    private World world;
    private WorldRenderer renderer;
    private ChestSearchDialog searchChestDlg;

    public SimpleThingsPanel(ExecutorService executorService) {
        this.executorService = executorService;
        initComponents();
    }

    @Override
    public JComponent getTabComponent() {
        return this;
    }

    @Override
    public void reset() {
        setWorld(null, null);
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        this.world = world;
        this.renderer = renderer;
        this.searchChestDlg = null;
        boolean enabled = world != null;
        btnLootChests.setEnabled(enabled);
        btnPlayerPos.setEnabled(enabled);
        btnSearchChests.setEnabled(enabled);
        btnSlimeChunks.setEnabled(enabled);
        btnSpawnChunk.setEnabled(enabled);
        btnTulpis.setEnabled(enabled);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnSlimeChunks = new javax.swing.JButton();
        btnPlayerPos = new javax.swing.JButton();
        btnSpawnChunk = new javax.swing.JButton();
        btnSearchChests = new javax.swing.JButton();
        btnLootChests = new javax.swing.JButton();
        btnTulpis = new javax.swing.JButton();

        btnSlimeChunks.setText("Highlight slime chunks");
        btnSlimeChunks.setEnabled(false);
        btnSlimeChunks.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSlimeChunksActionPerformed(evt);
            }
        });

        btnPlayerPos.setText("Highlight player position");
        btnPlayerPos.setEnabled(false);
        btnPlayerPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPlayerPosActionPerformed(evt);
            }
        });

        btnSpawnChunk.setText("Highlight spawn chunk");
        btnSpawnChunk.setEnabled(false);
        btnSpawnChunk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSpawnChunkActionPerformed(evt);
            }
        });

        btnSearchChests.setText("Search chests ...");
        btnSearchChests.setEnabled(false);
        btnSearchChests.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSearchChestsActionPerformed(evt);
            }
        });

        btnLootChests.setText("Highlight loot chests");
        btnLootChests.setEnabled(false);
        btnLootChests.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLootChestsActionPerformed(evt);
            }
        });

        btnTulpis.setText("Tulpis in Plains");
        btnTulpis.setEnabled(false);
        btnTulpis.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTulpisActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnSlimeChunks, javax.swing.GroupLayout.DEFAULT_SIZE, 388, Short.MAX_VALUE)
                    .addComponent(btnPlayerPos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnSpawnChunk, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnSearchChests, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnLootChests, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnTulpis, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnSlimeChunks)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnPlayerPos)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSpawnChunk)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSearchChests)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnLootChests)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnTulpis)
                .addContainerGap(102, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnSlimeChunksActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSlimeChunksActionPerformed
        if(renderer != null) {
            long seed = world.getRandomSeed();
            renderer.highlight(world.chunks().filter(c -> c.isSlimeChunk(seed))
                        .map(ChunkHighlightEntry::new));
        }
    }//GEN-LAST:event_btnSlimeChunksActionPerformed

    private void btnPlayerPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPlayerPosActionPerformed
        highlight(ChunkHighlightEntry.of(world.getPlayerChunk()));
    }//GEN-LAST:event_btnPlayerPosActionPerformed

    private void btnSpawnChunkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSpawnChunkActionPerformed
        highlight(ChunkHighlightEntry.of(world.getSpawnChunk()));
    }//GEN-LAST:event_btnSpawnChunkActionPerformed

    private void btnSearchChestsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSearchChestsActionPerformed
        if(world == null)
            return;
        if(searchChestDlg == null) {
            final World world = this.world;
            final ChestSearchDialog dlg = new ChestSearchDialog(SwingUtilities.getWindowAncestor(this));
            AsyncExecution.submitNoThrow(executorService, () -> world.getLevel()
                    .getCompound("fml")
                    .getCompound("Registries")
                    .getCompound("minecraft:item")
                    .getList("ids", NBTTagCompound.class).stream()
                    .map(item -> item.getString("K"))
                    .filter(Objects::nonNull)
                    .sorted().collect(Collectors.toList()),
                    result -> dlg.installAutoCompletion(result));
            searchChestDlg = dlg;
        }
        if(!searchChestDlg.run())
            return;
        final var item = searchChestDlg.getItem();
        final var itemPred = MCItem.filterByID(item);
        highlightTileEntity(tile -> MCItem.getChestContent(tile).anyMatch(itemPred),
                "Chests containing " + item, nbt -> createChestView(nbt, item));
    }//GEN-LAST:event_btnSearchChestsActionPerformed

    private void btnLootChestsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLootChestsActionPerformed
        highlightTileEntity(t -> t.getString("LootTable") != null,
                "Loot chests details for ");
    }//GEN-LAST:event_btnLootChestsActionPerformed

    private void btnTulpisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTulpisActionPerformed
        final Map<Integer, Biome> biomeRegistry = world.getBiomeRegistry();
        Optional<Biome> optPlains = biomeRegistry.values().stream()
                .filter(b -> "minecraft:plains".equals(b.namespacedID)).findAny();
        if(optPlains.isPresent()) {
            final Noise noise = new Noise(new Random(2345));
            int plainsID = optPlains.get().numericID;
            highlight(world.getChunks().parallelStream().flatMap(chunk -> {
                final int chunkX = chunk.getGlobalX() << 4;
                final int chunkZ = chunk.getGlobalZ() << 4;
                final NBTIntArray biomes = chunk.getBiomes();
                final ChunkHighlightEntry.WithOverlay og =
                        new ChunkHighlightEntry.WithOverlay(chunk);
                for(int idx=0 ; idx<256 ; ++idx) {
                    if(biomes.getInt(idx) == plainsID) {
                        final int x = idx & 15;
                        final int z = idx >> 4;
                        final double value = noise.nose2d(
                                (chunkX + x) / 200.0,
                                (chunkZ + z) / 200.0);
                        if(value < -0.8)
                            og.setRGB(x, z, 0xFFFF0000);
                    }
                }
                return og.stream();
            }));
        } else
            highlight(Stream.empty());
    }//GEN-LAST:event_btnTulpisActionPerformed

    private void highlight(Stream<? extends WorldRenderer.HighlightEntry> highlights) {
        if(renderer != null)
            renderer.highlight(highlights);
    }

    private void highlightTileEntity(final Predicate<NBTTagCompound> filter, final String title) {
        highlightTileEntity(filter, title, null);
    }

    private void highlightTileEntity(final Predicate<NBTTagCompound> filter, final String title,
            Function<NBTTagCompound, Stream<Map.Entry<String, ? extends JComponent>>> createTabs) {
        highlight(world.chunks().filter(chunk -> chunk.tileEntities()
                .anyMatch(filter))
                .map(chunk -> new TileEntityHighlightEntry(chunk, title, filter, createTabs)));
    }

    private Stream<Map.Entry<String, ? extends JComponent>>
         createChestView(NBTTagCompound nbt, String highlightItem) {
        final var id = nbt.getString("id");
        if(id == null)
            return Stream.empty();
        final var items = MCItem.getChestContent(nbt, id)
                .collect(Collectors.toList());
        final var model = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return items.size();
            }

            @Override
            public int getColumnCount() {
                return 4;
            }

            @Override
            public String getColumnName(int column) {
                switch (column) {
                    case 0: return "Slot";
                    case 1: return "Item";
                    case 2: return "Count";
                    case 3: return "NBT";
                    default:
                        throw new AssertionError();
                }
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                final var item = items.get(rowIndex);
                switch (columnIndex) {
                    case 0: return item.slot;
                    case 1: return item.id;
                    case 2: return item.count;
                    case 3: return item.tag.isEmpty() ? "" :
                            item.tag.size() + " values";
                    default:
                        throw new AssertionError();
                }
            }
        };
        final var title = id + " at " + NBTTreeModel.formatPosition(nbt);
        final var table = new JTable(model);
        final var fontMetrics = table.getFontMetrics(table.getFont());
        table.getColumnModel().getColumn(0)
                .setWidth(fontMetrics.stringWidth("123"));
        ContextMenuMouseListener.install(table, (e, row, column) -> {
            final var item = items.get(row);
            final var popupMenu = new JPopupMenu();
            switch (column) {
                case 0:
                    break;
                case 1:
                    popupMenu.add(new AbstractAction("Copy item ID") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            ContextMenuMouseListener.copyToClipboard(item.id);
                        }
                    });
                    break;
                case 2:
                    break;
                case 3:
                    if(!item.tag.isEmpty()) {
                        popupMenu.add(new AbstractAction("Show NBT") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                NBTTreeModel.displayNBT(table, item.tag, item.id);
                            }
                        });
                        if(item.id.equals("minecraft:filled_map")) {
                            popupMenu.add(new AbstractAction("Load markers into map panel") {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    world.loadMapMarkers(item.tag);
                                }
                            });
                        }
                    }
                    break;
                default:
                    throw new AssertionError();
            }
            return popupMenu;
        }, true);
        for(int idx=0 ; idx<items.size() ; idx++) {
            if(items.get(idx).id.equals(highlightItem))
                table.getSelectionModel().addSelectionInterval(idx, idx);
        }
        return Stream.of(new AbstractMap.SimpleImmutableEntry<>(
                title, NBTTreeModel.wrapInScrollPane(table)));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnLootChests;
    private javax.swing.JButton btnPlayerPos;
    private javax.swing.JButton btnSearchChests;
    private javax.swing.JButton btnSlimeChunks;
    private javax.swing.JButton btnSpawnChunk;
    private javax.swing.JButton btnTulpis;
    // End of variables declaration//GEN-END:variables

    private class TileEntityHighlightEntry extends ChunkHighlightEntry {
        private final String titlePrefix;
        private final Predicate<NBTTagCompound> filter;
        private final Function<NBTTagCompound, Stream<Map.Entry<String, ? extends JComponent>>> createTabs;

        public TileEntityHighlightEntry(Chunk chunk, String titlePrefix, Predicate<NBTTagCompound> filter,
                Function<NBTTagCompound, Stream<Map.Entry<String, ? extends JComponent>>> createTabs) {
            super(chunk);
            this.titlePrefix = titlePrefix;
            this.filter = filter;
            this.createTabs = createTabs;
        }

        @Override
        public void showDetailsFor(Component parent) {
            final var combinedNbt = chunk.tileEntities()
                    .filter(filter)
                    .collect(NBTTagList.toTagList(NBTTagCompound.class));
            final var title = titlePrefix + this;
            if(createTabs == null)
                NBTTreeModel.displayNBT(parent, combinedNbt, title);
            else {
                final var tabs = chunk.tileEntities()
                        .filter(filter)
                        .flatMap(createTabs)
                        .collect(Collectors.toList());
                NBTTreeModel.displayNBT(parent, combinedNbt, title, tabs);
            }
        }
    }
}
