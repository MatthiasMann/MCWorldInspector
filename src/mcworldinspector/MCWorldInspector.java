package mcworldinspector;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import mcworldinspector.utils.Expected;

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
    private final JList<String> blockList = new JList<>();
    private final JList<WorldRenderer.HighlightEntry> highlightList = new JList<>();
    private final JTextField blockListFilterTF = new JTextField();
    private Set<String> blocktypes = Collections.EMPTY_SET;
    private WorldRenderer renderer;

    public MCWorldInspector(String[] args) {
        super("MC World Inspector");
        this.preferences = Preferences.userNodeForPackage(MCWorldInspector.class);
    }
    
    private void openWorld() {
        JFileChooser jfc = new JFileChooser(preferences.get("recent_folder", "."));
        jfc.addChoosableFileFilter(MCA_FILE_FILTER);
        jfc.setFileFilter(MCA_FILE_FILTER);
        if(jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            preferences.put("recent_folder", jfc.getCurrentDirectory().getAbsolutePath());
            File folder = jfc.getSelectedFile().getParentFile();
            String worldName = getWorldName(folder);
            loadWorld(worldName, folder.listFiles(MCA_FILE_FILTER));
            //loadWorld(worldName, new File[]{ jfc.getSelectedFile() });
        }
    }
    
    private String getWorldName(File path) {
        while(path != null && !new File(path, "level.dat").exists())
            path = path.getParentFile();
        return path != null ? path.getName() : "Unnamed";
    }

    private class LoadWorldDialog extends ProgressBarDialog {
        //final HashSet<RegionFile> regions = new HashSet<>();
        final HashSet<Chunk> chunks = new HashSet<>();
        final ArrayList<Exception> errors = new ArrayList<>();
        final TreeSet<String> blocktypes = new TreeSet<>();
        final Iterator<File> files;
        final AtomicInteger openFiles = new AtomicInteger();
        final ExecutorService executor = Executors.newWorkStealingPool();
        int progress = 0;
        int total = 0;

        public LoadWorldDialog(Collection<File> files, Frame parent, boolean modal) {
            super(parent, modal);
            this.total = files.size();
            this.files = files.iterator();
        }

        public void start() {
            submitAsyncLoads();
        }

        private void submitAsyncLoads() {
            while(openFiles.get() < 10 && files.hasNext()) {
                final File file = files.next();
                try {
                    total += RegionFile.loadAsync(file, executor, openFiles, chunk ->
                        EventQueue.invokeLater(
                                () -> processResult(chunk)));
                } catch(IOException ex) {
                    errors.add(ex);
                }
                ++progress;
            }
            setMaximum(total);
            setValue(progress);
        }

        void processResult(Expected<Chunk> v) {
            try {
                Chunk chunk = v.get();
                chunks.add(chunk);
                chunk.getBlockTypes().forEach(blocktypes::add);
            } catch(Exception e) {
                errors.add(e);
            }
            setValue(++progress);
            submitAsyncLoads();
            if(progress == total) {
                assert(!files.hasNext());
                executor.shutdown();
                setVisible(false);
                dispose();
                finishedLoadingWorld(this);
            }
        }
    }

    private void loadWorld(String world_name, final File[] files) {
        // free up memory
        if(renderer != null) {
            mainarea.setViewportView(null);
            renderer = null;
        }
        if(files.length == 0)
            return;
        final LoadWorldDialog dialog = new LoadWorldDialog(
                Arrays.asList(files), this, true);
        dialog.setTitle("Loading world " + world_name);
        dialog.start();
        dialog.setVisible(true);
    }

    private void finishedLoadingWorld(LoadWorldDialog result) {
        renderer = new WorldRenderer(result.chunks);
        mainarea.setViewportView(renderer);
        renderer.startChunkRendering();
        highlightList.setModel(renderer.getHighlightsModel());
        blocktypes = result.blocktypes;
        blocktypes.remove("minecraft:air");
        blocktypes.remove("minecraft:cave_air");
        blocktypes.remove("minecraft:bedrock");
        buildBlockListModel();
    }
    
    private void buildBlockListModel() {
        String filter = blockListFilterTF.getText();
        final List<String> filtered = blocktypes.stream().filter(e ->
            filter.isEmpty() || e.contains(filter)).collect(Collectors.toList());
        blockList.setModel(new AbstractListModel() {
            @Override
            public int getSize() {
                return filtered.size();
            }

            @Override
            public Object getElementAt(int index) {
                return filtered.get(index);
            }
        });
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

        blockList.addListSelectionListener((e) -> {
            final ListModel<String> model = blockList.getModel();
            renderer.highlightBlocks(blockList.getSelectedValuesList());
        });
        highlightList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        highlightList.addListSelectionListener((e) -> {
            WorldRenderer.HighlightEntry value = highlightList.getSelectedValue();
            if(renderer != null && value != null)
                renderer.scrollTo(value);
        });
        highlightList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    WorldRenderer.HighlightEntry value = highlightList.getSelectedValue();
                    List<String> blockTypes = blockList.getSelectedValuesList();
                    if(value != null && !blockTypes.isEmpty()) {
                        final List<SubChunk.BlockPos> blocks = blockTypes.stream()
                                .flatMap(value.chunk::findBlocks).collect(Collectors.toList());
                        JList list = new JList(new AbstractListModel<SubChunk.BlockPos>() {
                            @Override
                            public int getSize() {
                                return blocks.size();
                            }
                            @Override
                            public SubChunk.BlockPos getElementAt(int index) {
                                return blocks.get(index);
                            }
                        });
                        JOptionPane.showMessageDialog(MCWorldInspector.this, list,
                                "Block positions", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });

        blockListFilterTF.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                buildBlockListModel();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                buildBlockListModel();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                buildBlockListModel();
            }
        });
        JScrollPane blockListSP = new JScrollPane(blockList);
        JPanel blockListPanel = new JPanel(null);
        GroupLayout blockListLayout = new GroupLayout(blockListPanel);
        blockListPanel.setLayout(blockListLayout);
        blockListLayout.setVerticalGroup(
                blockListLayout.createSequentialGroup()
                        .addComponent(blockListFilterTF, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(blockListSP, GroupLayout.DEFAULT_SIZE, 1000, Short.MAX_VALUE));
        blockListLayout.setHorizontalGroup(
                blockListLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(blockListFilterTF)
                        .addComponent(blockListSP));

        JScrollPane highlightListSP = new JScrollPane(highlightList);
        JSplitPane infoSplitpane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, blockListPanel, highlightListSP);
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
