package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class AuctionHistoryGui implements FluxGui {

    public record AuctionHistoryEntry(
            int id,
            String itemName,
            double price,
            String otherParty,
            String type,
            long timestamp
    ) {}

    private static final int PAGE_SIZE  = 45;
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_BACK  = 48;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_NEXT  = 53;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final FluxMarket plugin;
    private final Player player;
    private final List<AuctionHistoryEntry> entries;
    private final Map<String, Double> stats;
    private final Inventory inventory;
    private int page = 0;

    public AuctionHistoryGui(FluxMarket plugin, Player player,
                             List<AuctionHistoryEntry> entries, Map<String, Double> stats) {
        this.plugin = plugin;
        this.player = player;
        this.entries = entries;
        this.stats = stats;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6AH History"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < entries.size(); i++) {
            inventory.setItem(i, buildEntryItem(entries.get(start + i)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemUtils.named(Material.ARROW, "&7Previous Page"));
        }
        if ((page + 1) * PAGE_SIZE < entries.size()) {
            inventory.setItem(SLOT_NEXT, ItemUtils.named(Material.ARROW, "&7Next Page"));
        }

        // Stats item
        double totalSpent  = stats.getOrDefault("total_spent",  0.0);
        double totalEarned = stats.getOrDefault("total_earned", 0.0);
        int totalBought    = stats.getOrDefault("total_bought", 0.0).intValue();
        int totalSold      = stats.getOrDefault("total_sold",   0.0).intValue();

        inventory.setItem(SLOT_STATS, ItemUtils.named(Material.GOLD_INGOT, "&6Transaction Stats",
                "&7Total spent:  &c$" + FormatUtils.formatMoney(totalSpent),
                "&7Total earned: &a$" + FormatUtils.formatMoney(totalEarned),
                "&7Items bought: &f" + totalBought,
                "&7Items sold:   &f" + totalSold));

        // Back button
        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.ARROW, "&7Back",
                "&7Return to Auction House"));
    }

    private ItemStack buildEntryItem(AuctionHistoryEntry entry) {
        boolean isBought = "BOUGHT".equalsIgnoreCase(entry.type());
        String date = DATE_FMT.format(Instant.ofEpochMilli(entry.timestamp()));

        if (isBought) {
            return ItemUtils.named(Material.PAPER,
                    "&aBought: " + entry.itemName(),
                    "&7Price: &a$" + FormatUtils.formatMoney(entry.price()),
                    "&7From: &f" + entry.otherParty(),
                    "&7Date: &f" + date);
        } else {
            return ItemUtils.named(Material.PAPER,
                    "&cSold: " + entry.itemName(),
                    "&7Price: &c$" + FormatUtils.formatMoney(entry.price()),
                    "&7To: &f" + entry.otherParty(),
                    "&7Date: &f" + date);
        }
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

        if (slot == SLOT_PREV && page > 0) { page--; populate(); return; }
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < entries.size()) { page++; populate(); return; }
        if (slot == SLOT_BACK) {
            new AuctionMainGui(plugin, player).open();
        }
    }
}
