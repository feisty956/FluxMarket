package me.fluxmarket.module.orders.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.orders.OrdersDao.MailboxEntry;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated chest GUI where order creators claim their delivered items.
 * Slots 0-44 show the actual items. Clicking one moves it to the player's inventory.
 */
public class OrderClaimGui implements FluxGui {

    private static final int PAGE_SLOTS  = 45;
    private static final int SLOT_PREV   = 45;
    private static final int SLOT_INFO   = 46;
    private static final int SLOT_CLAIM_ALL = 49;
    private static final int SLOT_BACK   = 53;
    private static final int SLOT_NEXT   = 52;

    private final FluxMarket plugin;
    private final Player player;
    private final List<MailboxEntry> entries;
    private final Inventory inventory;
    private int page = 0;

    public OrderClaimGui(FluxMarket plugin, Player player, List<MailboxEntry> entries) {
        this.plugin = plugin;
        this.player = player;
        this.entries = new ArrayList<>(entries);
        inventory = org.bukkit.Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Order Mailbox"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = PAGE_SLOTS; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SLOTS;
        for (int i = 0; i < PAGE_SLOTS && (start + i) < entries.size(); i++) {
            inventory.setItem(i, entries.get(start + i).item().clone());
        }

        if (page > 0)
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        if ((page + 1) * PAGE_SLOTS < entries.size())
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));

        int total = entries.stream().mapToInt(e -> e.item().getAmount()).sum();
        inventory.setItem(SLOT_INFO, ItemUtils.named(Material.CHEST,
                "&6Order Mailbox",
                "&7Unclaimed items: &f" + total,
                "&7Click an item to take it.",
                "&eShift-click &7for full stacks."));

        if (!entries.isEmpty()) {
            inventory.setItem(SLOT_CLAIM_ALL, ItemUtils.named(Material.HOPPER,
                    "&aClaim All",
                    "&7Move all items to your inventory."));
        }

        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.BARRIER, "&cBack"));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (slot == SLOT_BACK)  { new OrdersMyGui(plugin, player, loadMyOrders()).open(); return; }
        if (slot == SLOT_PREV && page > 0) { page--; populate(); return; }
        if (slot == SLOT_NEXT)  { page++; populate(); return; }

        if (slot == SLOT_CLAIM_ALL) {
            claimAll();
            return;
        }

        if (slot >= PAGE_SLOTS) return;
        int idx = page * PAGE_SLOTS + slot;
        if (idx >= entries.size()) return;

        claimEntry(idx);
    }

    private void claimEntry(int idx) {
        MailboxEntry entry = entries.get(idx);
        var overflow = player.getInventory().addItem(entry.item().clone());
        if (!overflow.isEmpty()) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNot enough inventory space!"));
            return;
        }
        plugin.getOrdersModule().getDao().removeMailboxEntry(entry.id());
        entries.remove(idx);
        // If we emptied this page, go back one
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SLOTS);
        if (page > maxPage) page = maxPage;
        populate();
    }

    private void claimAll() {
        List<MailboxEntry> toRemove = new ArrayList<>();
        for (MailboxEntry entry : entries) {
            var overflow = player.getInventory().addItem(entry.item().clone());
            if (overflow.isEmpty()) {
                toRemove.add(entry);
            } else {
                // Inventory full — stop here
                break;
            }
        }
        if (toRemove.isEmpty()) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNot enough inventory space!"));
            return;
        }
        for (MailboxEntry e : toRemove) {
            plugin.getOrdersModule().getDao().removeMailboxEntry(e.id());
            entries.remove(e);
        }
        int claimed = toRemove.size();
        player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                + "&aClaimed &f" + claimed + " &astack(s) from your order mailbox."));
        page = 0;
        populate();
    }

    /** Reload the player's active orders for the back button. */
    private List<me.fluxmarket.module.orders.Order> loadMyOrders() {
        return plugin.getOrdersModule().getDao().loadByPlayer(player.getUniqueId());
    }
}
