package mcworldinspector;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.LayoutStyle;
import javax.swing.tree.TreePath;
import mcworldinspector.nbt.NBTIntArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;

/**
 *
 * @author matthias
 */
public class StructureTypesPanel extends AbstractFilteredPanel<String> {
    private final ExecutorService executorService;
    private final JComboBox<Mode> cbHighlightMode;
    private Set<String> structureTypes = Collections.emptySet();

    public StructureTypesPanel(ExecutorService executorService) {
        this.executorService = executorService;
        setName("Structures");

        cbHighlightMode = new JComboBox<>(Mode.values());
        cbHighlightMode.addActionListener(e -> doHighlighting());

        final var labelHM = new JLabel("Highlight mode");
        labelHM.setLabelFor(cbHighlightMode);

        horizontal.addGroup(layout.createSequentialGroup()
                .addComponent(labelHM)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbHighlightMode));
        vertical.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(labelHM)
                .addComponent(cbHighlightMode));
    }

    @Override
    public void reset() {
        structureTypes = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks().flatMap(Chunk::structureTypes)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            structureTypes = result;
            buildListModel();
        });
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(structureTypes, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        final var mode = (Mode)cbHighlightMode.getSelectedItem();
        return world.getChunks().parallelStream()
                .flatMap(chunk -> chunk.structures()
                        .filter(Chunk.filterByID(selected))
                        .flatMap(structure -> mode.create(world, chunk, structure)));
    }

    private static void showStructureDetails(Component parent, Chunk startChunk, NBTTagCompound structure) {
        NBTTreeModel.displayNBT(parent, structure, structureDetailsTitle(structure, startChunk));
    }

    private static String structureDetailsTitle(NBTTagCompound structure, Chunk startChunk) {
        return "Stucture details for " + structure.getString("id") +
                " starting at <" + startChunk.x + ", " + startChunk.z + ">";
    }

    private static class BBHighlightEntry implements WorldRenderer.HighlightEntry {
        private final Chunk startChunk;
        private final NBTTagCompound structure;
        private final List<Rectangle> bbList;
        private final Rectangle outline;

        public BBHighlightEntry(Chunk startChunk, NBTTagCompound structure, List<Rectangle> bbList) {
            this.startChunk = startChunk;
            this.structure = structure;
            this.bbList = bbList;
            this.outline = new Rectangle(1, -1);
            bbList.forEach(outline::add);
        }

        @Override
        public int getX() {
            return outline.x;
        }

        @Override
        public int getZ() {
            return outline.y;
        }

        @Override
        public int getWidth() {
            return outline.width;
        }

        @Override
        public int getHeight() {
            return outline.height;
        }

        @Override
        public boolean contains(Point p) {
            return bbList.stream().anyMatch(r -> r.contains(p));
        }

        @Override
        public void paint(Graphics g, int zoom) {
            bbList.forEach(r -> g.fillRect(r.x * zoom, r.y * zoom,
                    r.width * zoom, r.height * zoom));
        }

        @Override
        public String toString() {
            return "starting at <" + startChunk.x + ", " + startChunk.z + ">";
        }

        @Override
        public void showDetailsFor(Component parent) {
            showStructureDetails(parent, startChunk, structure);
        }

        @Override
        public void showDetailsFor(Component parent, Point clicked) {
            final var model = new NBTTreeModel(structure);
            final var tt = NBTTreeModel.createNBTreeTable(model);
            model.getRoot().findChild("Children").ifPresent(children -> {
                final var clickedChildren =
                        structure.getList("Children", NBTTagCompound.class)
                        .entryStream()
                        .filter(e -> {
                            final var bb = toRectangle(e.value);
                            return bb != null && bb.contains(clicked);
                        })
                        .flatMap(e -> {
                            if(e.index >= children.size())
                                return Stream.empty();
                            final var child = children.getChild(e.index);
                            return Stream.of(new TreePath(new Object[]{
                                model.getRoot(),
                                children,
                                child
                            }));
                        })
                        .collect(Collectors.toList());
                if(clickedChildren.isEmpty())
                    return;
                final var tree = tt.getTree();
                clickedChildren.forEach(tree::expandPath);
                tree.setSelectionPaths(clickedChildren.toArray(TreePath[]::new));
                final var firstPath = clickedChildren.get(0);
                EventQueue.invokeLater(() -> {
                    final var r = tree.getPathBounds(firstPath);
                    if(r == null)
                        return;
                    final var r2 = tree.getPathBounds(
                            NBTTreeModel.getPathForLastChild(firstPath));
                    if(r2 != null)
                        r.add(r2);
                    tree.scrollRectToVisible(r);
                });
            });
            JOptionPane.showMessageDialog(parent,
                    NBTTreeModel.wrapInScrollPane(tt),
                    structureDetailsTitle(structure, startChunk),
                    JOptionPane.PLAIN_MESSAGE);
        }
    }

    static class CCHE extends ChunkHighlightEntry {
        private final Chunk startChunk;
        private final NBTTagCompound structure;

        public CCHE(Chunk startChunk, NBTTagCompound structure, Chunk chunk) {
            super(chunk);
            this.startChunk = startChunk;
            this.structure = structure;
        }

        @Override
        public void showDetailsFor(Component parent) {
            showStructureDetails(parent, startChunk, structure);
        }
    }

    static NBTIntArray getBoundingBox(NBTTagCompound nbt) {
        return nbt.get("BB", NBTIntArray.class);
    }

    static Rectangle toRectangle(NBTIntArray bb) {
        if(bb == null || bb.size() != 6)
            return null;
        final var x0 = bb.getInt(0);
        final var z0 = bb.getInt(2);
        final var width = bb.getInt(3) - x0 + 1;
        final var height = bb.getInt(5) - z0 + 1;
        return new Rectangle(x0, z0, width, height);
    }

    static Rectangle toRectangle(NBTTagCompound nbt) {
        return toRectangle(getBoundingBox(nbt));
    }

    public static enum Mode {
        BB("Bounding box") {
            @Override
            public Stream<? extends WorldRenderer.HighlightEntry> create(World world, Chunk startChunk, NBTTagCompound structure) {
                final var bb = toRectangle(structure);
                if(bb == null)
                    return Stream.empty();
                return Stream.of(new BBHighlightEntry(startChunk, structure,
                        Collections.singletonList(bb)));
            }
        },
        BB_CHUNKS("Chunks in bounding box") {
            @Override
            public Stream<? extends WorldRenderer.HighlightEntry> create(World world, Chunk startChunk, NBTTagCompound structure) {
                return world.chunks(getBoundingBox(structure))
                        .map(chunk -> new CCHE(startChunk, structure, chunk));
            }
        },
        CHILDREN("Children") {
            @Override
            public Stream<? extends WorldRenderer.HighlightEntry> create(World world, Chunk startChunk, NBTTagCompound structure) {
                final var bbList = structure
                        .getList("Children", NBTTagCompound.class)
                        .stream()
                        .map(StructureTypesPanel::toRectangle)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if(bbList.isEmpty())
                    return Stream.empty();
                return Stream.of(new BBHighlightEntry(startChunk, structure, bbList));
            }
        },
        CHILDREN_CHUNKS("Chunks in children") {
            @Override
            public Stream<? extends WorldRenderer.HighlightEntry> create(World world, Chunk startChunk, NBTTagCompound structure) {
                return structure.getList("Children", NBTTagCompound.class)
                    .stream()
                    .map(StructureTypesPanel::getBoundingBox)
                    .flatMap(world::chunks)
                    .distinct()
                    .map(chunk -> new CCHE(startChunk, structure, chunk));
            }
        },
        CENTER_CHUNK("Center chunk") {
            @Override
            public Stream<? extends WorldRenderer.HighlightEntry> create(World world, Chunk startChunk, NBTTagCompound structure) {
                return Stream.of(new CCHE(startChunk, structure, startChunk));
            }
        };

        public final String text;

        private Mode(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }

        public abstract Stream<? extends WorldRenderer.HighlightEntry> create(
            World world, Chunk startChunk, NBTTagCompound structure);
    }
}
