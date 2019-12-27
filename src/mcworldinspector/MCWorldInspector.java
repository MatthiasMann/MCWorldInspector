package mcworldinspector;

import mcworldinspector.utils.ProgressBarDialog;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileHelpers;
import mcworldinspector.utils.MultipleErrorsDialog;
import mcworldinspector.utils.StatusBar;

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
    private final TreeMap<String, AbstractFilteredPanel<?>> filteredPanels = new TreeMap<>();
    private final SimpleThingsPanel simpleThingsPanel = new SimpleThingsPanel(this::getRenderer);
    private final HighlightListPanel highlightListPanel = new HighlightListPanel();
    private final StatusBar statusBar = new StatusBar();
    private final JTextField statusBarCursorPos = new JTextField();
    private World world;
    private WorldRenderer renderer;

    public MCWorldInspector(String[] args) {
        super("MC World Inspector");
        this.preferences = Preferences.userNodeForPackage(MCWorldInspector.class);
        
        filteredPanels.put("Blocks", new BlockTypesPanel(this::getRenderer));
        filteredPanels.put("Entities", new EntityTypesPanel(this::getRenderer));
        filteredPanels.put("Sheep", new SheepColorPanel(this::getRenderer));
        filteredPanels.put("Tile Entities", new TileEntityTypesPanel(this::getRenderer));
        filteredPanels.put("Biomes", new BiomeTypesPanel(this::getRenderer));
        filteredPanels.put("Structures", new StructureTypesPanel(this::getRenderer));
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

    private void closeWorld() {
        World oldWorld = world;
        renderer = null;
        world = null;
        mainarea.setViewportView(null);
        filteredPanels.values().forEach(AbstractFilteredPanel::reset);
        highlightListPanel.setRenderer(null);
        firePropertyChange("world", oldWorld, world);
    }

    private void loadWorld(File folder) {
        // free up memory
        closeWorld();
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
        World oldWorld = this.world;
        this.world = world;
        renderer = new WorldRenderer(world);
        mainarea.setViewportView(renderer);
        renderer.startChunkRendering();
        highlightListPanel.setRenderer(renderer);
        filteredPanels.values().forEach(p -> p.setWorld(world));
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                final Point p = renderer.mouse2mc(e.getPoint());
                statusBarCursorPos.setText("X="+p.x+" Y="+p.y);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                final Point p = renderer.mouse2mc(e.getPoint());
                highlightListPanel.selectFromRenderer(p, e.getClickCount());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                statusBarCursorPos.setText("");
            }
        };
        renderer.addMouseMotionListener(ma);
        renderer.addMouseListener(ma);
        firePropertyChange("world", oldWorld, world);
    }

    @SuppressWarnings("UseSpecificCatch")
    private void openNBT() {
        JFileChooser jfc = new JFileChooser(preferences.get("recent_folder_nbt", "."));
        if(jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            preferences.put("recent_folder_nbt", jfc.getCurrentDirectory().getAbsolutePath());
            final File file = jfc.getSelectedFile();
            try {
                ByteBuffer buffer = FileHelpers.loadFile(file, 1<<20);
                NBTTagCompound nbt = NBTTagCompound.parseGuess(buffer);
                NBTTreeModel.displayNBT(this, nbt, file.getAbsolutePath());
            } catch(Exception ex) {
                MultipleErrorsDialog dlg = new MultipleErrorsDialog(this, true,
                        Collections.singletonList(new FileError(file, ex)));
                dlg.setVisible(true);
            }
        }
    }
    protected abstract class WorldAction extends AbstractAction {
        @SuppressWarnings("OverridableMethodCallInConstructor")
        public WorldAction(String name) {
            super(name);
            setEnabled(false);
            MCWorldInspector.this.addPropertyChangeListener(
                    "world", e -> setEnabled(world != null));
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        JMenu filemenu = new JMenu("File");
        filemenu.setMnemonic('F');
        JMenuItem openWorld = filemenu.add(new AbstractAction("Open world") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openWorld();
            }
        });
        openWorld.setMnemonic('O');
        openWorld.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_MASK));
        JMenuItem closeWorld = filemenu.add(new WorldAction("Close world") {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeWorld();
            }
        });
        closeWorld.setMnemonic('C');
        filemenu.addSeparator();
        JMenuItem openNBT = filemenu.add(new AbstractAction("Open NBT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openNBT();
            }
        });
        openNBT.setMnemonic('N');
        menubar.add(filemenu);
        JMenu viewmenu = new JMenu("View");
        viewmenu.setMnemonic('V');
        JMenuItem viewLevelDat = viewmenu.add(new WorldAction("level.dat") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(world != null)
                    NBTTreeModel.displayNBT(MCWorldInspector.this, world.getLevel(), "level.dat");
            }
        });
        viewLevelDat.setMnemonic('l');
        menubar.add(viewmenu);
        return menubar;
    }
    
    private void run() {
        setJMenuBar(createMenuBar());

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
        filteredPanels.entrySet().forEach(e -> tabbed.add(e.getKey(), e.getValue()));
        tabbed.add("Misc", simpleThingsPanel);

        JSplitPane infoSplitpane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbed, highlightListPanel);
        infoSplitpane.setDividerLocation(900);
        infoSplitpane.setResizeWeight(0.75);

        JSplitPane mainSplitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainarea, infoSplitpane);
        mainSplitpane.setDividerLocation(1300);
        mainSplitpane.setResizeWeight(1.0);
        
        statusBarCursorPos.setEditable(false);
        statusBarCursorPos.setColumns(16);
        statusBar.addElement(new StatusBar.Element(StatusBar.Alignment.RIGHT, statusBarCursorPos));

        JPanel panel = new JPanel(null);
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(mainSplitpane)
                .addComponent(statusBar));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(mainSplitpane, GroupLayout.DEFAULT_SIZE, 1000, Short.MAX_VALUE)
                //.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
        add(panel);
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
