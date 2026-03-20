package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AuctionMyListingsGui implements FluxGui {

    private static final int SLOT_BACK = 49;
    private final FluxMarket plugin;
    private final Player player;
    private final AuctionMainGui parent;
    private final Inventory inventory;
    private List<AuctionItem> myListings;

    public AuctionMyListingsGui(FluxMarket plugin, Player player, AuctionMainGui parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        inventory = Bukkit.createInventory(null, 54,
                FormatUtils.comp("&8» &6My Listings"));
        populate();
    }

    private void populate() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);
        inventory.setItem(SLOT_BACK, ItemUtils.named(Material.ARROW, "&7Back"));

        myListings = plugin.getAuctionModule().getManager().getByPlayer(player.getUniqueId());
        for (int i = 0; i < myListings.size() && i < 45; i++) {
            AuctionItem ai = myListings.get(i);
            ItemStack display = ai.getItem();
            var meta = display.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(FormatUtils.comp("&7Price: &f" + FormatUtils.formatMoney(ai.getPrice())));
            lore.add(FormatUtils.comp("&7Expires: &f" + FormatUtils.formatDuration(ai.getRemainingMillis())));
            if (ai.isBid() && ai.getHighestBidder() != null) {
                lore.add(FormatUtils.comp("&7Top Bid: &a" + FormatUtils.formatMoney(ai.getCurrentBid())));
            }
            lore.add(FormatUtils.comp(""));
            lore.add(FormatUtils.comp("&cRight-click &7— Cancel"));
            meta.lore(lore);
            display.setItemMeta(meta);
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

        if (slot == SLOT_BACK) { parent.open(); return; }
        if (slot >= 45 || slot >= myListings.size()) return;

        if (event.isRightClick()) {
            AuctionItem cancelled = plugin.getAuctionModule().getManager()
                    .cancelListing(myListings.get(slot).getUuid(), player.getUniqueId(), false);
            if (cancelled != null) {
                player.getInventory().addItem(cancelled.getItem());
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + "&aListing cancelled. Item returned to inventory.");
                populate();
            }
        }
    }
}
