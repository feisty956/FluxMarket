package me.fluxmarket.module.sell.wand;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.sell.SellService;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SellWandListener implements Listener {

    private final FluxMarket plugin;
    private final SellWandManager wandManager;

    public SellWandListener(FluxMarket plugin, SellWandManager wandManager) {
        this.plugin = plugin;
        this.wandManager = wandManager;
    }

    /** Convenience overload — service is resolved via plugin at event time. */
    public SellWandListener(FluxMarket plugin, SellWandManager wandManager, SellService ignored) {
        this(plugin, wandManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click block with main hand
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isContainer(block.getType())) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        UUID wandUuid = wandManager.getWandUuidFromItem(item);
        if (wandUuid == null) return;

        // Cancel the normal block interaction so the chest doesn't open
        event.setCancelled(true);

        // Load wand (sync — fast single-row lookup)
        SellWand wand = wandManager.getWand(wandUuid);
        if (wand == null) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThis sell wand could not be found in the database."));
            return;
        }

        if (!wand.isValid()) {
            String reason = wand.getType() == SellWand.WandType.USE ? "no uses remaining" : "expired";
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThis sell wand is no longer valid (" + reason + ")."));
            item.setAmount(0);
            wandManager.deleteWand(wandUuid);
            return;
        }

        // Get container inventory
        if (!(block.getState() instanceof Container container)) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThis block is not a valid container."));
            return;
        }

        Inventory containerInv = container.getInventory();
        List<ItemStack> contents = Arrays.stream(containerInv.getContents())
                .filter(i -> i != null && !i.getType().isAir())
                .toList();

        if (contents.isEmpty()) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cThe container is empty."));
            return;
        }

        // Sell contents
        SellService service = plugin.getSellModule().getService();
        SellService.SellResult result = service.sell(player, contents);

        if (result.isEmpty()) {
            player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                    + "&cNo sellable items found in the container."));
            return;
        }

        // Feedback
        String prefix = plugin.getConfigManager().getPrefix();
        int totalAmount = result.soldAmounts().values().stream().mapToInt(Integer::intValue).sum();
        String feedback = "&aSell Wand: Sold &f" + totalAmount + " &aitems for &f$"
                + FormatUtils.formatMoney(result.totalEarned());

        if (plugin.getConfigManager().isSellActionbar()) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(FormatUtils.color(feedback)));
        } else {
            player.sendMessage(FormatUtils.color(prefix + feedback));
        }

        // Decrement use and update
        wand.decrementUse();
        wandManager.updateWandItem(item, wand);

        if (!wand.isValid()) {
            // Wand depleted
            item.setAmount(0);
            wandManager.deleteWand(wandUuid);
            player.sendMessage(FormatUtils.color(prefix + "&cYour Sell Wand has been consumed."));
        } else {
            wandManager.saveWand(wand);
        }
    }

    private boolean isContainer(Material material) {
        if (material == null) return false;
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, DISPENSER, DROPPER, HOPPER -> true;
            default -> Tag.SHULKER_BOXES.isTagged(material);
        };
    }
}
