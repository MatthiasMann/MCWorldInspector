package mcworldinspector;

import java.awt.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;

/**
 *
 * @author matthias
 */
public class SimpleThingsPanel extends javax.swing.JPanel {
    private final Supplier<WorldRenderer> renderer;

    public SimpleThingsPanel(Supplier<WorldRenderer> renderer) {
        this.renderer = renderer;
        initComponents();
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
        btnSlimeChunks.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSlimeChunksActionPerformed(evt);
            }
        });

        btnPlayerPos.setText("Highlight player position");
        btnPlayerPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPlayerPosActionPerformed(evt);
            }
        });

        btnSpawnChunk.setText("Highlight spawn chunk");
        btnSpawnChunk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSpawnChunkActionPerformed(evt);
            }
        });

        btnSearchChests.setText("Search chests ...");
        btnSearchChests.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSearchChestsActionPerformed(evt);
            }
        });

        btnLootChests.setText("Highlight loot chests");
        btnLootChests.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLootChestsActionPerformed(evt);
            }
        });

        btnTulpis.setText("Tulpis in Plains");
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
        final WorldRenderer r = renderer.get();
        if(r != null)
            r.highlight(world -> {
                long seed = world.getRandomSeed();
                return world.chunks().filter(c -> c.isSlimeChunk(seed))
                        .map(chunk -> new HighlightEntry(chunk));
            });
    }//GEN-LAST:event_btnSlimeChunksActionPerformed

    private void btnPlayerPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPlayerPosActionPerformed
        final WorldRenderer r = renderer.get();
        if(r != null)
            r.highlight(world -> {
                Chunk chunk = world.getPlayerChunk();
                if(chunk != null)
                    return Stream.of(new HighlightEntry(chunk));
                return Stream.empty();
            });
    }//GEN-LAST:event_btnPlayerPosActionPerformed

    private void btnSpawnChunkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSpawnChunkActionPerformed
        final WorldRenderer r = renderer.get();
        if(r != null)
            r.highlight(world -> {
                Chunk chunk = world.getSpawnChunk();
                if(chunk != null)
                    return Stream.of(new HighlightEntry(chunk));
                return Stream.empty();
            });
    }//GEN-LAST:event_btnSpawnChunkActionPerformed

    private ChestSearchDialog searchChestDlg;

    private void btnSearchChestsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSearchChestsActionPerformed
        if(searchChestDlg == null)
            searchChestDlg = new ChestSearchDialog(SwingUtilities.getWindowAncestor(this));
        if(!searchChestDlg.run())
            return;
        final String item = searchChestDlg.getItem();
        final WorldRenderer r = renderer.get();
        if(r == null)
            return;
        r.highlight(new TileEntityHighlighter(tile -> {
                String id = tile.getString("id");
                return ("minecraft:chest".equals(id) || "minecraft:barrel".equals(id)) &&
                        tile.getList("Items", NBTTagCompound.class)
                                .stream().anyMatch(i -> item.equals(i.getString("id")));
            }, "Loot chests details for "));
    }//GEN-LAST:event_btnSearchChestsActionPerformed

    private void btnLootChestsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLootChestsActionPerformed
        final WorldRenderer r = renderer.get();
        if(r == null)
            return;
        r.highlight(new TileEntityHighlighter(
                t -> t.getString("LootTable") != null,
                "Loot chests details for "));
    }//GEN-LAST:event_btnLootChestsActionPerformed

    private void btnTulpisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTulpisActionPerformed
        final WorldRenderer r = renderer.get();
        if(r == null)
            return;
        final Noise noise = new Noise(new Random(2345));
        r.highlight(world -> {
            final Map<Integer, Biome> biomeRegistry = world.getBiomeRegistry();
            Optional<Biome> optPlains = biomeRegistry.values().stream()
                    .filter(b -> "minecraft:plains".equals(b.namespacedID)).findAny();
            if(optPlains.isPresent()) {
                int plainsID = optPlains.get().numericID;
                return world.getChunks().parallelStream().flatMap(chunk -> {
                    final int chunkX = chunk.getGlobalX() << 4;
                    final int chunkZ = chunk.getGlobalZ() << 4;
                    final NBTIntArray biomes = chunk.getBiomes();
                    final HighlightEntry.OverlayGen og = new HighlightEntry.OverlayGen();
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
                    return og.createHighlightEntry(chunk);
                });
            }
            return Stream.empty();
        });
    }//GEN-LAST:event_btnTulpisActionPerformed

    private static class TileEntityHighlighter implements WorldRenderer.HighlightSelector {
        private final Predicate<NBTTagCompound> filter;
        private final String title;

        public TileEntityHighlighter(Predicate<NBTTagCompound> filter, String title) {
            this.filter = filter;
            this.title = title;
        }

        @Override
        public Stream<HighlightEntry> apply(World world) {
            return world.chunks().filter(chunk ->
                    chunk.tileEntities().anyMatch(filter))
                    .map(chunk -> new HighlightEntry(chunk));
        }

        @Override
        public void showDetailsFor(Component parent, HighlightEntry entry) {
            NBTTagList<NBTTagCompound> result = entry.chunk.tileEntities()
                    .filter(filter).collect(NBTTagList.toTagList(NBTTagCompound.class));
            NBTTreeModel.displayNBT(parent, result, title + entry);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnLootChests;
    private javax.swing.JButton btnPlayerPos;
    private javax.swing.JButton btnSearchChests;
    private javax.swing.JButton btnSlimeChunks;
    private javax.swing.JButton btnSpawnChunk;
    private javax.swing.JButton btnTulpis;
    // End of variables declaration//GEN-END:variables
}
