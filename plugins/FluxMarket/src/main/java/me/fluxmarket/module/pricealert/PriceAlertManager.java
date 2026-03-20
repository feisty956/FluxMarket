package me.fluxmarket.module.pricealert;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.auction.AuctionItem;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PriceAlertManager {

    private final FluxMarket plugin;
    private final PriceAlertDao dao;
    // Keyed by player UUID -> list of active alerts
    private final Map<UUID, List<PriceAlert>> alerts = new ConcurrentHashMap<>();

    public PriceAlertManager(FluxMarket plugin, PriceAlertDao dao) {
        this.plugin = plugin;
        this.dao = dao;
        loadAll();
    }

    private void loadAll() {
        alerts.clear();
        for (PriceAlert alert : dao.getAllAlerts()) {
            alerts.computeIfAbsent(alert.playerUuid(), k -> new ArrayList<>()).add(alert);
        }
        plugin.getLogger().info("PriceAlertManager: loaded "
                + alerts.values().stream().mapToInt(List::size).sum() + " price alerts.");
    }

    /** Called when a new AH listing is created. Checks all alerts against the new item. */
    public void checkAlerts(AuctionItem item) {
        Material material = item.getItem().getType();
        double price = item.getPrice();

        for (Map.Entry<UUID, List<PriceAlert>> entry : alerts.entrySet()) {
            UUID playerUuid = entry.getKey();
            List<PriceAlert> playerAlerts = entry.getValue();

            List<PriceAlert> toRemove = new ArrayList<>();
            for (PriceAlert alert : playerAlerts) {
                if (alert.material() == material && price <= alert.targetPrice()) {
                    toRemove.add(alert);
                    notifyPlayer(playerUuid, alert, item);
                }
            }

            for (PriceAlert alert : toRemove) {
                playerAlerts.remove(alert);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                        () -> dao.removeAlert(playerUuid, alert.material()));
            }
        }
    }

    private void notifyPlayer(UUID playerUuid, PriceAlert alert, AuctionItem item) {
        String prefix = plugin.getConfigManager().getPrefix();
        String msg = prefix + "&e" + FormatUtils.formatMaterialName(alert.material().name())
                + " &7is now listed for &a$" + FormatUtils.formatMoney(item.getPrice())
                + " &7(your alert: &a$" + FormatUtils.formatMoney(alert.targetPrice()) + "&7) — check &e/ah";
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(FormatUtils.color(msg));
            }
        });
    }

    /** Add an alert for a player. Replaces any existing alert for the same material. */
    public void addAlert(Player player, Material material, double targetPrice) {
        UUID uuid = player.getUniqueId();
        List<PriceAlert> playerAlerts = alerts.computeIfAbsent(uuid, k -> new ArrayList<>());

        // Remove existing alert for same material (in memory)
        playerAlerts.removeIf(a -> a.material() == material);

        PriceAlert alert = new PriceAlert(uuid, material, targetPrice);
        playerAlerts.add(alert);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            dao.removeAlert(uuid, material);
            dao.addAlert(uuid, material, targetPrice);
        });
    }

    /** Remove an alert for a player. */
    public void removeAlert(Player player, Material material) {
        UUID uuid = player.getUniqueId();
        List<PriceAlert> playerAlerts = alerts.get(uuid);
        if (playerAlerts != null) {
            playerAlerts.removeIf(a -> a.material() == material);
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> dao.removeAlert(uuid, material));
    }

    /** Remove a specific alert (used from GUI). */
    public void removeAlert(UUID playerUuid, Material material) {
        List<PriceAlert> playerAlerts = alerts.get(playerUuid);
        if (playerAlerts != null) {
            playerAlerts.removeIf(a -> a.material() == material);
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> dao.removeAlert(playerUuid, material));
    }

    /** Get all alerts for a player (snapshot). */
    public List<PriceAlert> getAlerts(UUID playerUuid) {
        List<PriceAlert> list = alerts.get(playerUuid);
        return list == null ? List.of() : List.copyOf(list);
    }
}
