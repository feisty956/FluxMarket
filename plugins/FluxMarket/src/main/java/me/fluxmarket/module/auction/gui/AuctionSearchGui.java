package me.fluxmarket.module.auction.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.gui.FluxGui;
import me.fluxmarket.gui.GuiListener;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Anvil-based search GUI.
 * Player renames the paper item in the first slot → result shows in slot 2.
 * On clicking the result, we extract the name and apply as filter.
 */
public class AuctionSearchGui implements FluxGui {

    private final FluxMarket plugin;
    private final Player player;
    private final AuctionMainGui parent;
    private Inventory inventory;

    public AuctionSearchGui(FluxMarket plugin, Player player, AuctionMainGui parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
    }

    public void open() {
        // Use chat input as fallback (most reliable without ProtocolLib)
        // We ask the player to type their search in chat
        player.closeInventory();
        GuiListener.close(player.getUniqueId());

        player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                + "&eType your search term in chat (or &ccancel &eto abort):"));

        // Register a one-time chat listener
        plugin.getServer().getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                        event.setCancelled(true);
                        org.bukkit.event.HandlerList.unregisterAll(this);
                        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(event.message());
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!text.equalsIgnoreCase("cancel")) {
                                parent.setFilter(text);
                                parent.refresh();
                            }
                            parent.open();
                        });
                    }
                }, plugin);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }
}
