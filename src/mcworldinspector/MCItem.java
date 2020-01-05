package mcworldinspector;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import mcworldinspector.nbt.NBTTagCompound;
import mcworldinspector.nbt.NBTTagList;

/**
 *
 * @author matthias
 */
public class MCItem {
    public final String id;
    public final int count;
    public final int slot;
    public final NBTTagCompound tag;

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
                    nbt.getList("Items", NBTTagCompound.class).stream(),
                    nbt.getList("inventory", NBTTagCompound.class).stream())
                    .flatMap(MCItem::ofVanilla);
        }
    }

    private static Stream<MCItem> ofVanilla(NBTTagCompound nbt) {
        final var count = nbt.get("Count", Byte.class, (byte)1);
        final var slot = nbt.get("Slot", Byte.class, (byte)0);
        final var tag = nbt.getCompound("tag");
        final var id = nbt.getString("id");
        return (id != null)
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

    private static Stream<MCItem> ofStorageDrawersSlot(Map.Entry<Integer, NBTTagCompound> e) {
        final var slot = e.getKey();
        final var count = e.getValue().get("Count", Integer.class, 1);
        final var item = e.getValue().getCompound("Item");
        final var tag = item.getCompound("tag");
        final var id = item.getString("id");
        return (id != null)
                ? Stream.of(new MCItem(id, count, slot, tag))
                : Stream.empty();
    }

    private static Stream<MCItem> ofStorageDrawersSlot(NBTTagCompound nbt, int count) {
        final var conv = nbt.get("Conv", Integer.class, 1);
        final var slot = nbt.get("Slot", Byte.class, (byte)0);
        final var id = nbt.getCompound("Item").getString("id");
        return (id != null)
                ? Stream.of(new MCItem(id, count / conv, slot, NBTTagCompound.EMPTY))
                : Stream.empty();
    }
}
