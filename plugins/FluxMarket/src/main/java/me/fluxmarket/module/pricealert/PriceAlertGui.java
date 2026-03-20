package me.fluxmarket.module.pricealert;

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
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PriceAlertGui implements FluxGui {

    private static final int[] ALERT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final FluxMarket plugin;
    private final Player player;
    private final Inventory inv;
    // Parallel array mapping slot index → alert, for click handling
    private final PriceAlert[] slotAlerts = new PriceAlert[ALERT_SLOTS.length];

    private PriceAlertGui(FluxMarket plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, 27, FormatUtils.comp("&6Price Alerts"));
        populate();
    }

    public static void open(FluxMarket plugin, Player player) {
        PriceAlertGui gui = new PriceAlertGui(plugin, player);
        player.openInventory(gui.inv);
        GuiListener.open(player.getUniqueId(), gui);
    }

    private void populate() {
        // Top row filler (slots 0-8)
        ItemStack filler = ItemUtils.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }

        // Close button at slot 22
        inv.setItem(22, ItemUtils.named(Material.BARRIER, "&cClose"));

        // Alert items in slots 10-16
        List<PriceAlert> alerts = plugin.getPriceAlertManager().getAlerts(player.getUniqueId());
        for (int i = 0; i < ALERT_SLOTS.length; i++) {
            if (i >= alerts.size()) break;
            PriceAlert alert = alerts.get(i);
            slotAlerts[i] = alert;

            ItemStack icon = buildAlertIcon(alert);
            inv.setItem(ALERT_SLOTS[i], icon);
        }
    }

    private ItemStack buildAlertIcon(PriceAlert alert) {
        Material mat = alert.material();
        // Try to use the material itself as the icon; fall back to PAPER for non-obtainable materials
        Material icon;
        try {
            ItemStack test = new ItemStack(mat);
            icon = test.getType().isItem() ? mat : Material.PAPER;
        } catch (Exception e) {
            icon = Material.PAPER;
        }
        return ItemUtils.named(icon,
                "&e" + FormatUtils.formatMaterialName(mat.name()),
                "&7Target: &a$" + FormatUtils.formatMoney(alert.targetPrice()),
                "&cClick to remove");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        // Close button
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        // Check alert slots
        for (int i = 0; i < ALERT_SLOTS.length; i++) {
            if (slot == ALERT_SLOTS[i] && slotAlerts[i] != null) {
                PriceAlert alert = slotAlerts[i];
                plugin.getPriceAlertManager().removeAlert(player.getUniqueId(), alert.material());
                String prefix = plugin.getConfigManager().getPrefix();
                player.sendMessage(FormatUtils.color(prefix + "&7Price alert for &e"
                        + FormatUtils.formatMaterialName(alert.material().name()) + " &7removed."));
                // Refresh GUI
                player.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin, () -> PriceAlertGui.open(plugin, player));
                return;
            }
        }
    }
}
