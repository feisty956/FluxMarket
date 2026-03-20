package me.fluxmarket.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public interface FluxGui {
    void handleClick(InventoryClickEvent event);
    default void handleClose(InventoryCloseEvent event) {}
    default void handleDrag(InventoryDragEvent event) { event.setCancelled(true); }
}
