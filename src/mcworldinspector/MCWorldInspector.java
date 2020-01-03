package mcworldinspector;

import java.awt.EventQueue;
import mcworldinspector.utils.ProgressBarDialog;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
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
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import static mcworldinspector.CreateColorMapDialog.BCM_EXTENSION_FILTER;
import static mcworldinspector.CreateColorMapDialog.RECENT_FOLDER_COLORMAP_KEY;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.FileError;
import mcworldinspector.utils.FileHelpers;
import mcworldinspector.utils.MemoryUsageIndicator;
import mcworldinspector.utils.MultipleErrorsDialog;
import mcworldinspector.utils.StatusBar;

/**
 *
 * @author matthias
 */
public class MCWorldInspector extends javax.swing.JFrame {

    private static final String ACTIVE_COLOR_MAP_KEY = "active_color_map";

    private final Preferences preferences;
    private final ExecutorService workerPool;
    private final JScrollPane mainarea = new JScrollPane();
    private final TreeMap<String, AbstractFilteredPanel<?>> filteredPanels;
    private final SimpleThingsPanel simpleThingsPanel;
    private final RenderOptionsPanel renderOptionsPanel;
    private final HighlightListPanel highlightListPanel;
    private final StatusBar statusBar = new StatusBar();
    private final JTextField statusBarCursorPos = new JTextField();
    private final JTextField statusBarBiome = new JTextField();
    private final JTextField statusBarBlockInfo = new JTextField();
    private File worldFolder;
    private World world;
    private WorldRenderer renderer;
    private BlockColorMap blockColorMap = BlockColorMap.EMPTY;

    public MCWorldInspector(String[] args) {
        super("MC World Inspector");
        this.preferences = Preferences.userNodeForPackage(MCWorldInspector.class);
        this.workerPool = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r,
                        "UI Worker " + threadNumber.getAndIncrement());
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        });

        simpleThingsPanel = new SimpleThingsPanel(this::getRenderer, workerPool);
        renderOptionsPanel = new RenderOptionsPanel(this::renderChunks);
        highlightListPanel = new HighlightListPanel();
        filteredPanels = new TreeMap<>();
        filteredPanels.put("Blocks", new BlockTypesPanel(this::getRenderer, workerPool));
        filteredPanels.put("Entities", new EntityTypesPanel(this::getRenderer, workerPool));
        filteredPanels.put("Sheep", new SheepColorPanel(this::getRenderer, workerPool));
        filteredPanels.put("Villagers", new VillagerPanel(this::getRenderer, workerPool));
        filteredPanels.put("Tile Entities", new TileEntityTypesPanel(this::getRenderer, workerPool));
        filteredPanels.put("Biomes", new BiomeTypesPanel(this::getRenderer, workerPool));
        filteredPanels.put("Structures", new StructureTypesPanel(this::getRenderer, workerPool));

        EventQueue.invokeLater(() -> {
            if(!loadBlockColorMap(preferences.get(ACTIVE_COLOR_MAP_KEY, "")))
                createColorMapAction.showDialog();
        });
    }

    public WorldRenderer getRenderer() {
        return renderer;
    }

    public void setBlockColorMap(BlockColorMap blockColorMap) {
        this.blockColorMap = Objects.requireNonNull(blockColorMap);
        if(renderer != null) {
            renderer.setBlockColorMap(blockColorMap);
            renderChunks();
        }
    }

    private boolean loadBlockColorMap(String path)  {
        if(path.isEmpty())
            return false;
        return loadBlockColorMap(new File(path));
    }

    private boolean loadBlockColorMap(File file)  {
        try{
            BlockColorMap bcm = BlockColorMap.load(file);
            if(!bcm.isEmpty()) {
                setBlockColorMap(bcm);
                preferences.put(ACTIVE_COLOR_MAP_KEY, file.getAbsolutePath());
                return true;
            }
        } catch(IOException ex) {
            MultipleErrorsDialog.show(this, "Error while loading block color map",
                    true, new FileError(file, ex));
        }
        return false;
    }

    private void loadBlockColorMap() {
        JFileChooser jfc = new JFileChooser(preferences.get(RECENT_FOLDER_COLORMAP_KEY, "."));
        jfc.addChoosableFileFilter(BCM_EXTENSION_FILTER);
        jfc.setFileFilter(BCM_EXTENSION_FILTER);
        if(jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            preferences.put(RECENT_FOLDER_COLORMAP_KEY, jfc.getCurrentDirectory().getAbsolutePath());
            loadBlockColorMap(jfc.getSelectedFile());
        }
    }

    private void scrollToPlayerorSpawn() {
        Chunk chunk = world.getPlayerChunk();
        if(chunk == null)
            chunk = world.getSpawnChunk();
        if(chunk != null)
            renderer.scrollTo(chunk, true);
    }

    private void openWorld() {
        final WorldFolderFileView fileView = new WorldFolderFileView();
        final FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Minecraft world folder";
            }
        };
        JFileChooser jfc = new JFileChooser(preferences.get("recent_folder", ".")) {
            @Override
            public void approveSelection() {
                final File selectedFile = getSelectedFile();
                if(selectedFile != null && selectedFile.isDirectory()) {
                    if(fileView.isRegionFolder(selectedFile))
                        super.approveSelection();
                    setCurrentDirectory(selectedFile);
                }
            }
        };
        jfc.setFileView(fileView);
        jfc.addChoosableFileFilter(fileFilter);
        jfc.setFileFilter(fileFilter);
        jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if(jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            preferences.put("recent_folder", jfc.getCurrentDirectory().getAbsolutePath());
            worldFolder = jfc.getSelectedFile();
            loadWorld(this::scrollToPlayerorSpawn);
        }
    }

    private void closeWorld() {
        World oldWorld = world;
        renderer = null;
        world = null;
        mainarea.setViewportView(null);
        filteredPanels.values().forEach(AbstractFilteredPanel::reset);
        simpleThingsPanel.setWorld(null);
        highlightListPanel.setRenderer(null);
        firePropertyChange("world", oldWorld, world);
    }

    private void reloadWorld() {
        Point scrollPos = mainarea.getViewport().getViewPosition();
        loadWorld(world != null 
                ? () -> mainarea.getViewport().setViewPosition(scrollPos)
                : this::scrollToPlayerorSpawn);
    }

    private void loadWorld(Runnable postLoadCB) {
        if(worldFolder == null)
            return;
        // free up memory
        closeWorld();
        final ProgressBarDialog dialog = new ProgressBarDialog(this, true);
        final World.AsyncLoading loading = new World.AsyncLoading((newWorld, errors) -> {
            dialog.setVisible(false);
            dialog.dispose();
            finishedLoadingWorld(newWorld);
            postLoadCB.run();
            if(!errors.isEmpty())
                MultipleErrorsDialog.show(this,
                        "Errors loading world " + newWorld.getName(), true, errors);
        });
        loading.addPropertyChangeListener(e -> {
            switch(e.getPropertyName()) {
                case "total": dialog.setMaximum(loading.getTotal()); break;
                case "progress": dialog.setValue(loading.getProgress()); break;
                case "levelName": dialog.setTitle("Loading world " + loading.getLevelName()); break;
            }
        });
        if(loading.start(worldFolder)) {
            dialog.setTitle("Loading world");
            dialog.setVisible(true);
        }
    }

    private void finishedLoadingWorld(World world) {
        World oldWorld = this.world;
        this.world = world;
        renderer = new WorldRenderer(world);
        renderer.setBlockColorMap(blockColorMap);
        mainarea.setViewportView(renderer);
        renderChunks();
        highlightListPanel.setRenderer(renderer);
        simpleThingsPanel.setWorld(world);
        filteredPanels.values().forEach(p -> p.setWorld(world));
        MouseAdapter ma = new MouseAdapter() {
            private int startMouseX;
            private int startMouseY;
            private Point startScrollPos;

            @Override
            public void mouseDragged(MouseEvent e) {
                JViewport vp = getViewport();
                if(vp != null) {
                    Point p = new Point(startScrollPos);
                    p.x += startMouseX - e.getXOnScreen();
                    p.y += startMouseY - e.getYOnScreen();
                    vp.setViewPosition(p);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                startMouseX = e.getXOnScreen();
                startMouseY = e.getYOnScreen();
                JViewport vp = getViewport();
                if(vp != null)
                    startScrollPos = vp.getViewPosition();
            }

            private JViewport getViewport() {
                return mainarea.getViewport();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMousePos = renderer.component2mc(e.getPoint());
                final Chunk chunk = getMouseChunk();
                if(chunk != null) {
                    final Biome biome = chunk.getBiome(lastMousePos.x & 15,
                            lastMousePos.y & 15, world.getBiomeRegistry());
                    statusBarBiome.setText(biome != null ? biome.name : "");
                    updateStatusBarBlockInfo(chunk);
                } else {
                    statusBarBiome.setText("");
                    statusBarBlockInfo.setText("");
                    updateStatusBarMousePos(null);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                lastMousePos = renderer.component2mc(e.getPoint());
                highlightListPanel.selectFromRenderer(lastMousePos, e.getClickCount());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lastMousePos = null;
                statusBarCursorPos.setText("");
                statusBarBiome.setText("");
                statusBarBlockInfo.setText("");
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                final int wheelRotation = e.getWheelRotation();
                if(e.isControlDown() && wheelRotation != 0) {
                    int newZoom = (wheelRotation > 0)
                            ? Math.max(renderer.getZoom() - 1, 1)
                            : Math.min(renderer.getZoom() + 1, MAX_ZOOM);
                    renderer.setZoom(newZoom, e.getPoint());
                }
            }
        };
        renderer.addMouseMotionListener(ma);
        renderer.addMouseWheelListener(ma);
        renderer.addMouseListener(ma);
        firePropertyChange("world", oldWorld, world);
    }

    private Point lastMousePos;

    private Chunk getMouseChunk() {
        return world.getChunk(lastMousePos.x >> 4, lastMousePos.y >> 4);
    }

    private void updateStatusBarBlockInfo() {
        final Chunk chunk = getMouseChunk();
        if(chunk != null)
            updateStatusBarBlockInfo(chunk);
        else
            statusBarBlockInfo.setText("");
    }

    private void updateStatusBarBlockInfo(Chunk chunk) {
        final int x = lastMousePos.x & 15;
        final int z = lastMousePos.y & 15;
        SubChunk.BlockInfo topBlock;
        switch (renderOptionsPanel.getMode()) {
            case SURFACE:
                topBlock = chunk.getTopBlockInfo(chunk.getHeightmap(true), x, z);
                break;
            case SURFACE_NO_LEAVES:
                topBlock = chunk.getTopBlockInfo(chunk.getHeightmap(false), x, z);
                break;
            case UNDERGROUND:
                topBlock = chunk.getCaveFloorBlockInfo(x, renderOptionsPanel.getLayer(), z);
                break;
            default:
                throw new AssertionError();
        }
        statusBarBlockInfo.setText((topBlock != null) ?
            SubChunk.BlockInfo.blockToString(
                    topBlock.block, new StringBuilder()).toString() : "");
        updateStatusBarMousePos(topBlock);
    }

    private void updateStatusBarMousePos(SubChunk.BlockInfo block) {
        if(block != null)
            statusBarCursorPos.setText("<"+block.x+", "+block.y+", " + block.z +'>');
        else
            statusBarCursorPos.setText("<"+lastMousePos.x+", ?, "+lastMousePos.y + '>');
    }

    private void renderChunks() {
        if(renderer == null)
            return;
        switch (renderOptionsPanel.getMode()) {
            case SURFACE:
                renderer.startChunkRendering((w,c) ->
                        WorldRenderer.renderChunksSurface(w, c, true));
                break;
            case SURFACE_NO_LEAVES:
                renderer.startChunkRendering((w,c) ->
                        WorldRenderer.renderChunksSurface(w, c, false));
                break;
            case UNDERGROUND: {
                final int layer = renderOptionsPanel.getLayer();
                renderer.startChunkRendering((w,c) ->
                        WorldRenderer.renderChunksUnderground(w, c, layer));
            }
        }
        if(lastMousePos != null)
            updateStatusBarBlockInfo();
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
                MultipleErrorsDialog.show(this, "Errors while loading " + file,
                        true, new FileError(file, ex));
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

    private static final int MAX_ZOOM = 4;

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        JMenuItem openWorld = fileMenu.add(new AbstractAction("Open world") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openWorld();
            }
        });
        openWorld.setMnemonic('O');
        openWorld.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        JMenuItem reloadWorld = fileMenu.add(new WorldAction("Reload world") {
            @Override
            public void actionPerformed(ActionEvent e) {
                reloadWorld();
            }
        });
        reloadWorld.setMnemonic('R');
        reloadWorld.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        JMenuItem closeWorld = fileMenu.add(new WorldAction("Close world") {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeWorld();
            }
        });
        closeWorld.setMnemonic('C');
        fileMenu.addSeparator();
        JMenuItem loadBCM = fileMenu.add(new AbstractAction("Load block color map") {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadBlockColorMap();
            }
        });
        loadBCM.setMnemonic('b');
        fileMenu.addSeparator();
        JMenuItem openNBT = fileMenu.add(new AbstractAction("Open NBT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openNBT();
            }
        });
        openNBT.setMnemonic('N');
        return fileMenu;
    }

    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic('V');
        JMenuItem zoomIn = viewMenu.add(new WorldAction("Zoom in") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(renderer != null)
                    renderer.setZoom(Math.min(renderer.getZoom() + 1, MAX_ZOOM));
            }
        });
        zoomIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0));
        JMenuItem zoomOut = viewMenu.add(new WorldAction("Zoom out") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(renderer != null)
                    renderer.setZoom(Math.max(renderer.getZoom() - 1, 1));
            }
        });
        zoomOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0));
        viewMenu.addSeparator();
        JMenuItem viewLevelDat = viewMenu.add(new WorldAction("level.dat") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(world != null)
                    NBTTreeModel.displayNBT(MCWorldInspector.this, world.getLevel(), "level.dat");
            }
        });
        viewLevelDat.setMnemonic('l');
        return viewMenu;
    }

    private final CreateColorMapAction createColorMapAction = new CreateColorMapAction();

    private JMenu createToolMenu() {
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic('T');
        JMenuItem createColorMap = toolsMenu.add(createColorMapAction);
        createColorMap.setMnemonic('C');
        return toolsMenu;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        menubar.add(createFileMenu());
        menubar.add(createViewMenu());
        menubar.add(createToolMenu());
        return menubar;
    }
    
    private void run() {
        setJMenuBar(createMenuBar());

        JTabbedPane tabbed = new JTabbedPane();
        filteredPanels.entrySet().forEach(e -> tabbed.add(e.getKey(), e.getValue()));
        tabbed.add("Misc", simpleThingsPanel);
        tabbed.add("Render Options", renderOptionsPanel);

        JSplitPane infoSplitpane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbed, highlightListPanel);
        infoSplitpane.setDividerLocation(900);
        infoSplitpane.setResizeWeight(0.75);

        JSplitPane mainSplitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainarea, infoSplitpane);
        mainSplitpane.setDividerLocation(1300);
        mainSplitpane.setResizeWeight(1.0);

        MemoryUsageIndicator statusBarMemoryUsage = new MemoryUsageIndicator(20);
        statusBarMemoryUsage.start();
        statusBar.addElement(new StatusBar.Element(StatusBar.Alignment.RIGHT, statusBarMemoryUsage));
        statusBarCursorPos.setEditable(false);
        statusBarCursorPos.setColumns(16);
        statusBar.addElement(new StatusBar.Element(StatusBar.Alignment.RIGHT, statusBarCursorPos));
        statusBarBiome.setEditable(false);
        statusBarBiome.setColumns(16);
        statusBarBiome.setToolTipText("Biome");
        statusBar.addElement(new StatusBar.Element(StatusBar.Alignment.RIGHT, statusBarBiome));
        statusBarBlockInfo.setEditable(false);
        statusBarBlockInfo.setColumns(50);
        statusBarBlockInfo.setToolTipText("Top block");
        statusBar.addElement(new StatusBar.Element(StatusBar.Alignment.LEFT, statusBarBlockInfo));

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

    static class WorldFolderFileView extends FileView {
        private static final URL REGION_FOLDER_IMAGE = WorldFolderFileView.class.getResource("region_folder.png");

        private final WeakHashMap<File, Boolean> specialCache = new WeakHashMap<>();

        @Override
        public Boolean isTraversable(File f) {
            if(isRegionFolder(f))
                return false;
            return null;
        }

        @Override
        public Icon getIcon(File f) {
            if(REGION_FOLDER_IMAGE != null && isRegionFolder(f))
                return new ImageIcon(REGION_FOLDER_IMAGE);
            return null;
        }

        @Override
        public String getTypeDescription(File f) {
            if(isRegionFolder(f))
                return "Minecraft world folder";
            return null;
        }

        boolean isRegionFolder(File folder) {
            if(folder == null)
                return false;
            Boolean cached = specialCache.get(folder);
            if(cached == null) {
                cached = Boolean.FALSE;
                if(folder.isDirectory() && !folder.getName().equals("poi")) {
                    try {
                        String[] files = folder.list(
                                (dir, name) -> name.endsWith(".mca"));
                        if(files != null && files.length > 0)
                            cached = Boolean.TRUE;
                    } catch(SecurityException ex) {}
                }
                specialCache.put(folder, cached);
            }
            return cached;
        }
    }

    private class CreateColorMapAction extends AbstractAction {
        private CreateColorMapDialog dlg;

        public CreateColorMapAction() {
            super("Create block color map");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showDialog();
        }

        public void showDialog() {
            if(dlg != null) {
                dlg.toFront();
                return;
            }
            dlg = new CreateColorMapDialog(MCWorldInspector.this, false);
            dlg.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    dlg = null;
                }
            });
            dlg.setColorMapListener(MCWorldInspector.this::setBlockColorMap);
            dlg.setColorMapSavedListener(file -> preferences.put(
                    ACTIVE_COLOR_MAP_KEY, file.getAbsolutePath()));
            dlg.setVisible(true);
        }
    }
}
