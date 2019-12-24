package mcworldinspector;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public class SlimeChunksPanel extends javax.swing.JPanel {
    private final Supplier<WorldRenderer> renderer;

    public SlimeChunksPanel(Supplier<WorldRenderer> renderer) {
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

        jHighlightButton = new javax.swing.JButton();

        jHighlightButton.setText("Highlight Slime Chunks");
        jHighlightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jHighlightButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jHighlightButton, javax.swing.GroupLayout.DEFAULT_SIZE, 388, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jHighlightButton)
                .addContainerGap(267, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jHighlightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jHighlightButtonActionPerformed
        final WorldRenderer r = renderer.get();
        if(r != null)
            r.highlight(new Highlighter());
    }//GEN-LAST:event_jHighlightButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jHighlightButton;
    // End of variables declaration//GEN-END:variables

    public static final class Highlighter implements WorldRenderer.HighlightSelector {
        @Override
        public List<WorldRenderer.HighlightEntry> apply(World world) {
            return world.slimeChunks()
                .map(chunk -> new WorldRenderer.HighlightEntry(chunk))
                .collect(Collectors.toList());
        }
    }
}
