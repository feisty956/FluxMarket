package me.fluxmarket.module.treasury;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TreasuryGui implements FluxGui {

    private static final int SLOT_HEADER    = 4;
    private static final int SLOT_AH_TAX    = 11;
    private static final int SLOT_TOTAL     = 13;
    private static final int SLOT_RECENT    = 15;
    private static final int SLOT_CLOSE     = 22;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inventory;

    public TreasuryGui(FluxMarket plugin, Player player,
                       double ahTaxTotal, double grandTotal,
                       List<TreasuryDao.TreasuryEntry> recent) {
        this.plugin = plugin;
        this.player = player;
        inventory = Bukkit.createInventory(null, 27, FormatUtils.comp("&8» &6Tax Treasury"));
        populate(ahTaxTotal, grandTotal, recent);
    }

    private void populate(double ahTaxTotal, double grandTotal,
                          List<TreasuryDao.TreasuryEntry> recent) {
        // Header
        inventory.setItem(SLOT_HEADER, ItemUtils.named(Material.GOLD_BLOCK,
                "&6Tax Treasury",
                "&7Server tax revenue overview."));

        // AH Tax total
        inventory.setItem(SLOT_AH_TAX, ItemUtils.named(Material.GOLD_INGOT,
                "&eAuction Tax",
                "&7Total collected from listing & sale fees:",
                "&a" + FormatUtils.formatMoney(ahTaxTotal)));

        // Grand total
        inventory.setItem(SLOT_TOTAL, ItemUtils.named(Material.EMERALD,
                "&eTotal Treasury",
                "&7Grand total across all sources:",
                "&a" + FormatUtils.formatMoney(grandTotal)));

        // Recent entries
        List<String> recentLore = new ArrayList<>();
        recentLore.add("&7Last " + recent.size() + " entries:");
        if (recent.isEmpty()) {
            recentLore.add("&8No entries yet.");
        } else {
            for (TreasuryDao.TreasuryEntry e : recent) {
                String ts = DATE_FMT.format(Instant.ofEpochMilli(e.timestamp()));
                recentLore.add("&8" + ts + " &7[&e" + e.source() + "&7] &a+"
                        + FormatUtils.formatMoney(e.amount()));
            }
        }
        inventory.setItem(SLOT_RECENT, ItemUtils.named(Material.CLOCK,
                "&eRecent Entries",
                recentLore.toArray(new String[0])));

        // Close
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose"));
    }

    public void open() {
        player.openInventory(inventory);
        GuiListener.open(player.getUniqueId(), this);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getSlot() == SLOT_CLOSE) {
            player.closeInventory();
        }
    }
}
