package mcworldinspector;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import mcworldinspector.nbt.NBTDoubleArray;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.AsyncExecution;
import mcworldinspector.utils.ContextMenuMouseListener;

/**
 *
 * @author matthias
 */
public class VillagerPanel extends AbstractFilteredPanel<String> {
    public static final String MINECRAFT_VILLAGER = "minecraft:villager";

    private final ExecutorService executorService;
    private Set<String> professions = Collections.emptySet();

    public VillagerPanel(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void reset() {
        professions = Collections.emptySet();
        super.reset();
    }

    @Override
    public void setWorld(World world, WorldRenderer renderer) {
        super.setWorld(world, renderer);
        AsyncExecution.submitNoThrow(executorService, () -> {
            return world.chunks()
                    .flatMap(chunk -> chunk.getEntities(MINECRAFT_VILLAGER))
                    .map(VillagerPanel::getProfession)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
        }, result -> {
            professions = result;
            buildListModel();
        });
    }

    static String getProfession(NBTTagCompound villager) {
        return villager.getCompound("VillagerData").getString("profession");
    }

    @Override
    protected List<String> filteredList(String filter) {
        return filteredStringList(professions, filter);
    }

    @Override
    protected Stream<? extends WorldRenderer.HighlightEntry> createHighlighter(List<String> selected) {
        final Predicate<NBTTagCompound> filter = villager ->
                selected.contains(getProfession(villager));
        return world.getChunks().parallelStream()
                .filter(chunk -> chunk.getEntities(MINECRAFT_VILLAGER)
                        .anyMatch(filter))
                .map(chunk -> new ChunkHighlightEntry(chunk) {
                    @Override
                    public void showDetailsFor(Component parent) {
                        final var list = chunk
                                .getEntities(MINECRAFT_VILLAGER).filter(filter)
                                .map(VillagerPanel::addVillagerLabel)
                                .collect(Collectors.toList());
                        final var tabs = list.stream()
                                .flatMap(VillagerPanel.this::createTradeView)
                                .collect(Collectors.toList());
                        NBTTreeModel.displayNBT(parent, new NBTTreeModel(list),
                                "Villager details for " + this, tabs);
                    }
                });
    }

    public static Map.Entry<String, NBTTagCompound> addVillagerLabel(NBTTagCompound entity) {
        final var villagerData = entity.getCompound("VillagerData");
        final var level = villagerData.get("level", Integer.class);
        final var profession = villagerData.getString("profession");
        final var pos = entity.get("Pos", NBTDoubleArray.class);
        final var label = new StringBuilder(profession);
        if(level != null)
            label.append(" (").append(level).append(')');
        if(pos != null && pos.size() == 3)
            label.append(" at ").append(NBTTreeModel.formatPosition(pos));
        return new AbstractMap.SimpleImmutableEntry<>(label.toString(), entity);
    }

    public static class Trade {
        public final byte buy0Count;
        public final String buy0Item;
        public final byte buy1Count;
        public final String buy1Item;
        public final byte sellCount;
        public final String sellItem;
        public final NBTTagCompound sellTag;
        public final int uses;
        public final int maxUses;

        public Trade(NBTTagCompound nbt) {
            final var buy = nbt.getCompound("buy");
            final var buyB = nbt.getCompound("buyB");
            final var sell = nbt.getCompound("sell");
            this.buy0Count = buy.get("Count", Byte.class, (byte)0);
            this.buy0Item = buy.get("id", String.class);
            this.buy1Count = buyB.get("Count", Byte.class, (byte)0);
            this.buy1Item = buyB.get("id", String.class);
            this.sellCount = sell.get("Count", Byte.class, (byte)0);
            this.sellItem = sell.get("id", String.class);
            this.sellTag = sell.getCompound("tag");
            this.uses = nbt.get("uses", Integer.class, 0);
            this.maxUses = nbt.get("maxUses", Integer.class, 0);
        }

        public boolean hasBuy0() {
            return MCItem.isValidItemID(buy0Item);
        }

        public boolean hasBuy1() {
            return MCItem.isValidItemID(buy1Item);
        }

        public boolean hasSell() {
            return MCItem.isValidItemID(sellItem);
        }

        public boolean sellsFilledMap() {
            return MCItem.FILLED_MAP.equals(sellItem);
        }

        public NBTTagList<NBTTagCompound> getMapDecorations() {
            return sellTag.getList("Decorations", NBTTagCompound.class);
        }
    }

    private Stream<Map.Entry<String, ? extends JComponent>> createTradeView(Map.Entry<String, NBTTagCompound> villager) {
        final var nbt = villager.getValue();
        final var trades = nbt.getCompound("Offers")
                .getList("Recipes", NBTTagCompound.class)
                .stream()
                .map(Trade::new)
                .collect(Collectors.toList());
        if(trades.isEmpty())
            return Stream.empty();
        final var model = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return trades.size();
            }

            @Override
            public int getColumnCount() {
                return 8;
            }

            @Override
            public String getColumnName(int column) {
                switch (column) {
                    case 0:
                    case 2: return "Buy Count";
                    case 1:
                    case 3: return "Buy Item";
                    case 4: return "Sell Count";
                    case 5: return "Sell Item";
                    case 6: return "Sell Tag";
                    case 7: return "Uses";
                    default:
                        throw new AssertionError();
                }
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                final var trade = trades.get(rowIndex);
                switch (columnIndex) {
                    case 0: return trade.hasBuy0() ? trade.buy0Count : "";
                    case 1: return trade.hasBuy0() ? trade.buy0Item : "";
                    case 2: return trade.hasBuy1() ? trade.buy1Count : "";
                    case 3: return trade.hasBuy1() ? trade.buy1Item : "";
                    case 4: return trade.hasSell() ? trade.sellCount : "";
                    case 5: return trade.hasSell() ? trade.sellItem : "";
                    case 6:
                        if(trade.sellTag.isEmpty())
                            return "";
                        if(trade.sellsFilledMap()) {
                            final var decorations = trade.getMapDecorations();
                            if(!decorations.isEmpty()) {
                                final var deco0 = decorations.get(0);
                                final var x = deco0.get("x", Double.class);
                                final var z = deco0.get("z", Double.class);
                                if(x != null && z != null)
                                    return String.format("<%.0f, %.0f>", x, z);
                            }
                        }
                        return trade.sellTag.size() + " values";
                    case 7: return trade.uses + " / " + trade.maxUses;
                    default:
                        throw new AssertionError();
                }
            }
        };
        final var table = new JTable(model);
        ContextMenuMouseListener.setTableColumnWidth(table, 0, "123");
        ContextMenuMouseListener.setTableColumnWidth(table, 2, "123");
        ContextMenuMouseListener.setTableColumnWidth(table, 4, "123");
        ContextMenuMouseListener.setTableColumnWidth(table, 7, "42 / 42");
        ContextMenuMouseListener.install(table, (e, row, column) -> {
            final var trade = trades.get(row);
            if(column == 6 && !trade.sellTag.isEmpty()) {
                final var popupMenu = new JPopupMenu();
                popupMenu.add(new AbstractAction("Show NBT") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        NBTTreeModel.displayNBT(table, trade.sellTag, trade.sellItem);
                    }
                });
                if(trade.sellsFilledMap()) {
                    popupMenu.add(new AbstractAction("Load markers into map panel") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            world.loadMapMarkers(trade.sellTag);
                        }
                    });
                }
                return popupMenu;
            }
            return null;
        }, true);
        return Stream.of(new AbstractMap.SimpleImmutableEntry<>(
                villager.getKey(), NBTTreeModel.wrapInScrollPane(table)));
    }
}
