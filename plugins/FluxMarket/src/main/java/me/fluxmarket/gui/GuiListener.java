package me.fluxmarket.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiListener implements Listener {

    private static final Map<UUID, FluxGui> openGuis = new ConcurrentHashMap<>();

    public static void open(UUID playerUuid, FluxGui gui) {
        openGuis.put(playerUuid, gui);
    }

    public static void close(UUID playerUuid) {
        openGuis.remove(playerUuid);
    }

    public static FluxGui get(UUID playerUuid) {
        return openGuis.get(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        FluxGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
        // Block double-click collect-to-cursor — it can pull items from GUI inventory
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        FluxGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
        gui.handleDrag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof org.bukkit.entity.Player player)) return;
        FluxGui gui = openGuis.remove(player.getUniqueId());
        if (gui != null) gui.handleClose(event);
    }
}
