package mcworldinspector;

import mcworldinspector.utils.ProgressBarDialog;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import mcworldinspector.utils.MultipleErrorsDialog;

/**
 *
 * @author matthias
 */
public class MCWorldInspector extends javax.swing.JFrame {

    private static final AbstractFileFilter MCA_FILE_FILTER = new AbstractFileFilter("Minecraft region files") {
        @Override
        public boolean accept(File pathname) {
            return pathname.getName().endsWith(".mca") || pathname.isDirectory();
        }
    };

    private final Preferences preferences;
    private final JScrollPane mainarea = new JScrollPane();
    private final BlockTypesPanel blockListPanel = new BlockTypesPanel(this::getRenderer);
    private final EntityTypesPanel entityListPanel = new EntityTypesPanel(this::getRenderer);
    private final SlimeChunksPanel slimeChunksPanel = new SlimeChunksPanel(this::getRenderer);
    private final HighlightListPanel highlightListPanel = new HighlightListPanel();
    private WorldRenderer renderer;

    public MCWorldInspector(String[] args) {
        super("MC World Inspector");
        this.preferences = Preferences.userNodeForPackage(MCWorldInspector.class);
    }

    public WorldRenderer getRenderer() {
        return renderer;
    }

    private void openWorld() {
        JFileChooser jfc = new JFileChooser(preferences.get("recent_folder", "."));
        jfc.addChoosableFileFilter(MCA_FILE_FILTER);
        jfc.setFileFilter(MCA_FILE_FILTER);
        if(jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            preferences.put("recent_folder", jfc.getCurrentDirectory().getAbsolutePath());
            File folder = jfc.getSelectedFile().getParentFile();
            loadWorld(folder);
        }
    }

    private void loadWorld(File folder) {
        // free up memory
        if(renderer != null) {
            mainarea.setViewportView(null);
            highlightListPanel.setRenderer(null);
            renderer = null;
        }
        final ProgressBarDialog dialog = new ProgressBarDialog(this, true);
        final World.AsyncLoading loading = new World.AsyncLoading(folder, (world, errors) -> {
            dialog.setVisible(false);
            dialog.dispose();
            finishedLoadingWorld(world);
            if(!errors.isEmpty())
                new MultipleErrorsDialog(this, rootPaneCheckingEnabled, errors).setVisible(true);
        });
        loading.addPropertyChangeListener(e -> {
            switch(e.getPropertyName()) {
                case "total": dialog.setMaximum(loading.getTotal()); break;
                case "progress": dialog.setValue(loading.getProgress()); break;
                case "levelName": dialog.setTitle("Loading world " + loading.getLevelName()); break;
            }
        });
        loading.start();
        dialog.setTitle("Loading world");
        dialog.setVisible(true);
    }

    private void finishedLoadingWorld(World world) {
        renderer = new WorldRenderer(world);
        mainarea.setViewportView(renderer);
        renderer.startChunkRendering();
        highlightListPanel.setRenderer(renderer);
        blockListPanel.setBlockTypes(world.getBlockTypes());
        entityListPanel.setEntities(world.getEntityTypes());
    }

    private void run() {
        JMenuBar menubar = new JMenuBar();
        JMenu filemenu = new JMenu("File");
        filemenu.add(new AbstractAction("Open") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openWorld();
            }
        });
        menubar.add(filemenu);
        setJMenuBar(menubar);
        
        MouseAdapter mouseAdapter = new MouseAdapter() {
            private int startMouseX;
            private int startMouseY;
            private Point startScrollPos;

            @Override
            public void mouseDragged(MouseEvent e) {
                JViewport vp = getViewport();
                if(vp != null) {
                    Point p = new Point(startScrollPos);
                    p.x += startMouseX - e.getX();
                    p.y += startMouseY - e.getY();
                    vp.setViewPosition(p);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                startMouseX = e.getX();
                startMouseY = e.getY();
                JViewport vp = getViewport();
                if(vp != null)
                    startScrollPos = vp.getViewPosition();
            }
            
            private JViewport getViewport() {
                return mainarea.getViewport();
            }
        };
        mainarea.addMouseListener(mouseAdapter);
        mainarea.addMouseMotionListener(mouseAdapter);

        JTabbedPane tabbed = new JTabbedPane();
        tabbed.add("Blocks", blockListPanel);
        tabbed.add("Entities", entityListPanel);
        tabbed.add("Slime Chunks", slimeChunksPanel);

        JSplitPane infoSplitpane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbed, highlightListPanel);
        infoSplitpane.setDividerLocation(900);
        JSplitPane mainSplitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainarea, infoSplitpane);
        mainSplitpane.setDividerLocation(1400);
        add(mainSplitpane);
        setSize(1600, 1200);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MCWorldInspector(args).run();
        });
    }
}
