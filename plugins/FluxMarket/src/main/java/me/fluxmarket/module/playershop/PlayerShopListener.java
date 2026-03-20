package me.fluxmarket.module.playershop;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import me.fluxmarket.util.ItemUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles player shop sign creation, destruction, and purchases.
 *
 * Sign format (written by player on wall sign attached to chest face):
 *   Line 0: [Shop]
 *   Line 1: <quantity>   (e.g. 64)
 *   Line 2: $<price>     (e.g. $5.00 or just 5)
 *   Line 3: (auto-set by plugin)
 */
public class PlayerShopListener implements Listener {

    private final FluxMarket plugin;
    private final PlayerShopManager manager;

    public PlayerShopListener(FluxMarket plugin, PlayerShopManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ── Sign creation ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        String line0 = ChatColor.stripColor(event.line(0) == null ? ""
                : net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(event.line(0))).trim();

        if (!line0.equalsIgnoreCase("[Shop]")) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("fluxmarket.playershop.create")) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cYou don't have permission to create player shops."));
            event.setCancelled(true);
            return;
        }

        // Check max shops per player
        int max = plugin.getConfigManager().getPlayerShopMaxPerPlayer();
        if (manager.getByOwner(player.getUniqueId()).size() >= max) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cYou already have the maximum number of shops (" + max + ")."));
            event.setCancelled(true);
            return;
        }

        // Parse quantity
        String line1Raw = stripLine(event.line(1));
        int quantity;
        try {
            quantity = Integer.parseInt(line1Raw.replace(",", "").trim());
            if (quantity < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cLine 2 must be a positive integer (quantity)."));
            event.setCancelled(true);
            return;
        }

        // Parse price
        String line2Raw = stripLine(event.line(2)).replace("$", "").replace(",", ".");
        double price;
        try {
            price = Double.parseDouble(line2Raw.trim());
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cLine 3 must be a positive price (e.g. $5.00)."));
            event.setCancelled(true);
            return;
        }

        // Find attached chest
        Block signBlock = event.getBlock();
        Block chestBlock = findAttachedChest(signBlock);
        if (chestBlock == null) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cPlace the sign directly on a chest."));
            event.setCancelled(true);
            return;
        }

        // Detect item from chest contents (optional — may be empty)
        String material = "UNKNOWN";
        Inventory chestInv = ((Chest) chestBlock.getState()).getInventory();
        for (ItemStack item : chestInv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                material = item.getType().name();
                break;
            }
        }

        // Build the shop
        Location sign = signBlock.getLocation();
        Location chest = chestBlock.getLocation();
        PlayerShop shop = new PlayerShop(
                UUID.randomUUID(),
                player.getUniqueId(), player.getName(),
                sign.getWorld().getName(),
                sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(),
                chest.getBlockX(), chest.getBlockY(), chest.getBlockZ(),
                quantity, price, material
        );

        manager.addShop(shop);

        // Update sign lines
        String itemLabel = material.equals("UNKNOWN") ? "Any Item" : FormatUtils.formatMaterialName(material);
        event.line(0, FormatUtils.comp("&8[&6Shop&8]"));
        event.line(1, FormatUtils.comp("&f" + quantity + "x"));
        event.line(2, FormatUtils.comp("&a$" + FormatUtils.formatMoney(price)));
        event.line(3, FormatUtils.comp("&7" + itemLabel));

        player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                + "&aShop created! Right-click the sign to open it."));
    }

    // ── Sign destruction ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String signKey = signKey(block.getLocation());
        PlayerShop shop = manager.getBySignKey(signKey);
        if (shop == null) return;

        // Allow owner or admin to break
        if (!event.getPlayer().getUniqueId().equals(shop.getOwnerUuid())
                && !event.getPlayer().hasPermission("fluxmarket.playershop.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cOnly the shop owner can remove this shop."));
            return;
        }

        manager.removeBySignKey(signKey);
        event.getPlayer().sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                + "&7Shop removed."));
    }

    // ── Purchase ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign)) return;

        String signKey = signKey(block.getLocation());
        PlayerShop shop = manager.getBySignKey(signKey);
        if (shop == null) return;

        event.setCancelled(true);

        Player buyer = event.getPlayer();

        // Owner right-clicks → show management info
        if (buyer.getUniqueId().equals(shop.getOwnerUuid())) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&eYour shop: &f" + shop.getQuantity() + "x "
                    + FormatUtils.formatMaterialName(shop.getMaterial())
                    + " &efor &a$" + FormatUtils.formatMoney(shop.getPrice())));
            return;
        }

        // Check buyer permission
        if (!buyer.hasPermission("fluxmarket.playershop.use")) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cYou don't have permission to buy from player shops."));
            return;
        }

        // Get chest
        Block chestBlock = block.getWorld().getBlockAt(shop.getChestX(), shop.getChestY(), shop.getChestZ());
        if (!(chestBlock.getState() instanceof Chest chest)) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cShop chest not found. Contact the owner."));
            return;
        }

        Inventory chestInv = chest.getInventory();

        // Detect current material from chest if still UNKNOWN
        if (shop.getMaterial().equals("UNKNOWN")) {
            for (ItemStack item : chestInv.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    shop.setMaterial(item.getType().name());
                    plugin.getPlayerShopModule().getDao().save(shop);
                    break;
                }
            }
        }

        if (shop.getMaterial().equals("UNKNOWN")) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThis shop is empty."));
            return;
        }

        Material mat;
        try {
            mat = Material.valueOf(shop.getMaterial());
        } catch (IllegalArgumentException e) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cShop has an invalid item type."));
            return;
        }

        // Count available in chest
        int available = 0;
        for (ItemStack item : chestInv.getContents()) {
            if (item != null && item.getType() == mat) available += item.getAmount();
        }

        if (available < shop.getQuantity()) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cShop doesn't have enough stock (&f" + available + "/" + shop.getQuantity() + "&c)."));
            return;
        }

        // Check buyer funds
        if (!plugin.getEconomyProvider().has(buyer, shop.getPrice())) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNot enough money! Need &f$" + FormatUtils.formatMoney(shop.getPrice())));
            return;
        }

        // Check buyer inventory space
        ItemStack toGive = new ItemStack(mat, shop.getQuantity());
        if (buyer.getInventory().firstEmpty() == -1
                && !buyer.getInventory().containsAtLeast(toGive, 1)) {
            buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cYour inventory is full."));
            return;
        }

        // Take items from chest
        int toTake = shop.getQuantity();
        for (ItemStack item : chestInv.getContents()) {
            if (item == null || item.getType() != mat || toTake <= 0) continue;
            if (item.getAmount() <= toTake) {
                toTake -= item.getAmount();
                chestInv.remove(item);
            } else {
                item.setAmount(item.getAmount() - toTake);
                toTake = 0;
            }
            if (toTake <= 0) break;
        }

        // Money transfer
        plugin.getEconomyProvider().withdraw(buyer, shop.getPrice());
        org.bukkit.OfflinePlayer seller = plugin.getServer().getOfflinePlayer(shop.getOwnerUuid());
        plugin.getEconomyProvider().deposit(seller, shop.getPrice());

        // Give items to buyer
        buyer.getInventory().addItem(new ItemStack(mat, shop.getQuantity()));

        String itemName = FormatUtils.formatMaterialName(mat.name());
        buyer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                + "&aBought &f" + shop.getQuantity() + "x " + itemName
                + " &afor &f$" + FormatUtils.formatMoney(shop.getPrice())));

        // Notify seller if online
        Player sellerPlayer = plugin.getServer().getPlayer(shop.getOwnerUuid());
        if (sellerPlayer != null) {
            sellerPlayer.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&e" + buyer.getName() + " &7bought &f" + shop.getQuantity() + "x " + itemName
                    + " &7from your shop for &a$" + FormatUtils.formatMoney(shop.getPrice())));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Block findAttachedChest(Block signBlock) {
        // Check if block data is a wall sign
        if (!(signBlock.getBlockData() instanceof org.bukkit.block.data.type.WallSign signData)) return null;
        Block attached = signBlock.getRelative(signData.getFacing().getOppositeFace());
        if (attached.getState() instanceof Chest) return attached;
        return null;
    }

    private String signKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private String stripLine(net.kyori.adventure.text.Component comp) {
        if (comp == null) return "";
        return ChatColor.stripColor(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(comp)).trim();
    }
}
