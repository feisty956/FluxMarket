package me.fluxmarket.gui;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Anvil-based text input GUI.
 *
 * Opens an ANVIL inventory with a PAPER item in slot 0.
 * The player types in the rename field; clicking slot 2 (result) captures the text.
 * Closing without confirming calls the callback with null.
 *
 * Usage:
 *   AnvilInputGui.open(plugin, player, "&6Enter amount", "64", value -> { ... });
 */
public class AnvilInputGui implements FluxGui {

    private final FluxMarket plugin;
    private final Player player;
    private final String placeholder;
    private final Consumer<String> callback;
    private final Inventory inventory;
    private boolean handled = false;

    private AnvilInputGui(FluxMarket plugin, Player player, String title, String placeholder,
                          Consumer<String> callback) {
        this.plugin = plugin;
        this.player = player;
        this.placeholder = placeholder;
        this.callback = callback;

        inventory = Bukkit.createInventory(null, InventoryType.ANVIL, FormatUtils.comp(title));
        // Slot 0: input item — name is shown as pre-filled text in rename box
        inventory.setItem(0, ItemUtils.named(Material.PAPER,
                placeholder == null || placeholder.isBlank() ? " " : placeholder));
    }

    /** Open an anvil input prompt and call {@code callback} with the typed text (or null on cancel). */
    public static void open(FluxMarket plugin, Player player, String title, String placeholder,
                            Consumer<String> callback) {
        AnvilInputGui gui = new AnvilInputGui(plugin, player, title, placeholder, callback);
        player.openInventory(gui.inventory);
        GuiListener.open(player.getUniqueId(), gui);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        // Only act on the output slot (slot 2)
        if (event.getSlot() != 2) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta() || !result.getItemMeta().hasDisplayName()) return;

        // Extract plain text (strip legacy color codes via §-serializer + ChatColor.stripColor)
        String text = org.bukkit.ChatColor.stripColor(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(result.getItemMeta().displayName())).trim();

        // Treat the placeholder itself or blank as "no input"
        if (text.equals(placeholder) || text.isBlank() || text.equals(" ")) text = null;

        handled = true;
        String finalText = text;
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(finalText));
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!handled) {
            handled = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(null));
        }
    }
}
