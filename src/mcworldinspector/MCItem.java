package mcworldinspector;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;
import mcworldinspector.nbttree.NBTTreeModel;
import mcworldinspector.utils.ContextMenuMouseListener;

/**
 *
 * @author matthias
 */
public class MCItem {
    public final String id;
    public final int count;
    public final int slot;
    public final NBTTagCompound tag;

    public static final String AIR = "minecraft:air";
    public static final String FILLED_MAP = "minecraft:filled_map";

    public boolean isFilledMap() {
        return FILLED_MAP.equals(id);
    }

    private MCItem(String id, int count, int slot, NBTTagCompound tag) {
        this.id = id;
        this.count = count;
        this.slot = slot;
        this.tag = tag;
    }

    public static Predicate<MCItem> filterByID(String id) {
        return item -> item.id.equals(id);
    }

    public static Stream<MCItem> getChestContent(NBTTagCompound nbt) {
        final var id = nbt.getString("id");
        if(id == null)
            return Stream.empty();
        return getChestContent(nbt, id);
    }

    public static boolean isValidItemID(final String id) {
        return id != null && !AIR.equals(id);
    }

    public static Stream<MCItem> getChestContent(NBTTagCompound nbt, String id) {
        switch (id) {
            case "storagedrawers:fractional_drawers_3":
                return ofStorageDrawers(nbt.getCompound("Drawers"));
            case "storagedrawers:standard_drawers_1":
            case "storagedrawers:standard_drawers_2":
            case "storagedrawers:standard_drawers_4":
                return ofStorageDrawers(nbt.getList("Drawers", NBTTagCompound.class));
            default:
                return Stream.concat(
                    nbt.getList("Items", NBTTagCompound.class).entryStream(),
                    nbt.getList("inventory", NBTTagCompound.class).entryStream())
                    .flatMap(MCItem::ofVanilla);
        }
    }

    public static Stream<MCItem> ofVanilla(NBTTagList.Entry<NBTTagCompound> e) {
        final var nbt = e.value;
        final var count = nbt.get("Count", Byte.class, (byte)1);
        final var slot = nbt.get("Slot", Byte.class, (byte)e.index);
        final var tag = nbt.getCompound("tag");
        final var id = nbt.getString("id");
        return isValidItemID(id)
                ? Stream.of(new MCItem(id, count, slot, tag))
                : Stream.empty();
    }

    private  static Stream<MCItem> ofStorageDrawers(NBTTagList<NBTTagCompound> nbt) {
        return nbt.entryStream().flatMap(MCItem::ofStorageDrawersSlot);
    }

    private  static Stream<MCItem> ofStorageDrawers(NBTTagCompound nbt) {
        final var count = nbt.get("Count", Integer.class, 1);
        return nbt.getList("Items", NBTTagCompound.class).stream().flatMap(
                item -> ofStorageDrawersSlot(item, count));
    }

    private static Stream<MCItem> ofStorageDrawersSlot(NBTTagList.Entry<NBTTagCompound> e) {
        final var slot = e.index;
        final var count = e.getValue().get("Count", Integer.class, 1);
        final var item = e.getValue().getCompound("Item");
        final var tag = item.getCompound("tag");
        final var id = item.getString("id");
        return isValidItemID(id)
                ? Stream.of(new MCItem(id, count, slot, tag))
                : Stream.empty();
    }

    private static Stream<MCItem> ofStorageDrawersSlot(NBTTagCompound nbt, int count) {
        final var conv = nbt.get("Conv", Integer.class, 1);
        final var slot = nbt.get("Slot", Byte.class, (byte)0);
        final var id = nbt.getCompound("Item").getString("id");
        return isValidItemID(id)
                ? Stream.of(new MCItem(id, count / conv, slot, NBTTagCompound.EMPTY))
                : Stream.empty();
    }

    public static JTable createInventoryView(World world, List<MCItem> items) {
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
        final var table = new JTable(model);
        ContextMenuMouseListener.setTableColumnWidth(table, 0, "123");
        ContextMenuMouseListener.setTableColumnWidth(table, 2, "123");
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
                        if(item.isFilledMap()) {
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
        return table;
    }
}
