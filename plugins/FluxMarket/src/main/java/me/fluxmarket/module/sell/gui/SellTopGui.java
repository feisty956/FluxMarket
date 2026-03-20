package me.fluxmarket.module.sell.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.sell.SellDao.SellTopEntry;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class SellTopGui implements FluxGui {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV    = 45;
    private static final int SLOT_FILLER1 = 46;
    private static final int SLOT_REFRESH = 48;
    private static final int SLOT_INFO    = 49;
    private static final int SLOT_FILLER2 = 52;
    private static final int SLOT_NEXT    = 53;

    private final FluxMarket plugin;
    private final Player player;
    private final List<SellTopEntry> entries;
    private final Inventory inventory;
    private int page = 0;

    public SellTopGui(FluxMarket plugin, Player player, List<SellTopEntry> entries) {
        this.plugin = plugin;
        this.player = player;
        this.entries = new ArrayList<>(entries);
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Sell Top"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        // Entry items
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < entries.size(); i++) {
            inventory.setItem(i, buildEntryItem(entries.get(start + i)));
        }

        // Navigation
        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page",
                    "&8Page " + page + " / " + totalPages()));
        }
        if ((page + 1) * PAGE_SIZE < entries.size()) {
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page",
                    "&8Page " + (page + 2) + " / " + totalPages()));
        }

        // Info
        Material infoMat;
        try { infoMat = Material.TOTEM_OF_UNDYING; }
        catch (Exception ignored) { infoMat = Material.GOLD_INGOT; }
        inventory.setItem(SLOT_INFO, ItemUtils.named(infoMat, "&6SellTop",
                "&7All-time top earners",
                "&7Total entries: &f" + entries.size()));

        // Refresh
        inventory.setItem(SLOT_REFRESH, ItemUtils.named(Material.COMPASS, "&eRefresh",
                "&7Click to reload the leaderboard"));

        // Fillers at 46, 52 (already set by loop above but ensure they're proper fillers)
        inventory.setItem(SLOT_FILLER1, filler);
        inventory.setItem(SLOT_FILLER2, filler);
    }

    private ItemStack buildEntryItem(SellTopEntry entry) {
        String rankColor = switch (entry.rank()) {
            case 1 -> "&6";
            case 2 -> "&7";
            case 3 -> "&c";
            default -> "&f";
        };

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        // Set player name so the skull tries to show the player's skin
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.playerName()));

        meta.displayName(FormatUtils.comp(rankColor + "#" + entry.rank() + " " + entry.playerName()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(FormatUtils.comp("&7Total earned: &a$" + FormatUtils.formatMoney(entry.totalEarned())));
        lore.add(FormatUtils.comp("&7Rank: &f#" + entry.rank()));
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil((double) entries.size() / PAGE_SIZE));
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

        if (slot == SLOT_PREV && page > 0) {
            page--;
            populate();
            return;
        }
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < entries.size()) {
            page++;
            populate();
            return;
        }
        if (slot == SLOT_REFRESH) {
            player.closeInventory();
            // Reload async, reopen on main thread
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<SellTopEntry> fresh = plugin.getSellModule().getDao().getSellTop(50);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new SellTopGui(plugin, player, fresh).open());
            });
        }
    }
}
