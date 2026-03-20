package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionDao;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AuctionMailboxGui implements FluxGui {

    private static final int SLOT_CLOSE = 49;
    private final FluxMarket plugin;
    private final Player player;
    private List<AuctionDao.MailboxEntry> entries;
    private Inventory inventory;

    public AuctionMailboxGui(FluxMarket plugin, Player player, List<AuctionDao.MailboxEntry> entries) {
        this.plugin = plugin;
        this.player = player;
        this.entries = entries;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6Mailbox"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);
        inventory.setItem(SLOT_CLOSE, ItemUtils.named(Material.BARRIER, "&cClose"));

        for (int i = 0; i < entries.size() && i < 45; i++) {
            AuctionDao.MailboxEntry entry = entries.get(i);
            ItemStack display;
            if (entry.itemData() != null && entry.itemData().length > 0) {
                display = ItemUtils.deserialize(entry.itemData());
                var meta = display.getItemMeta();
                var lore = new java.util.ArrayList<net.kyori.adventure.text.Component>();
                lore.add(FormatUtils.comp("&7Reason: &f" + entry.reason()));
                if (entry.money() > 0) lore.add(FormatUtils.comp("&7Money: &a+" + FormatUtils.formatMoney(entry.money())));
                lore.add(FormatUtils.comp("&eClick &7— Claim"));
                meta.lore(lore);
                display.setItemMeta(meta);
            } else {
                // Money-only entry
                display = ItemUtils.named(Material.GOLD_NUGGET, "&aEarnings",
                        "&7Reason: &f" + entry.reason(),
                        "&7Amount: &a+" + FormatUtils.formatMoney(entry.money()),
                        "&eClick &7— Claim");
            }
            inventory.setItem(i, display);
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
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
        if (slot >= 45 || slot >= entries.size()) return;

        AuctionDao.MailboxEntry entry = entries.get(slot);
        // Give item
        if (entry.itemData() != null && entry.itemData().length > 0) {
            ItemStack item = ItemUtils.deserialize(entry.itemData());
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix() + "&cYour inventory is full!"));
                return;
            }
            player.getInventory().addItem(item);
        }
        // Give money
        if (entry.money() > 0) {
            plugin.getEconomyProvider().deposit(player, entry.money());
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&a+" + FormatUtils.formatMoney(entry.money()) + " &a(" + entry.reason() + ")"));
        }
        plugin.getAuctionModule().getDao().deleteMailboxEntry(entry.id());
        entries.remove(slot);
        populate();
    }
}
