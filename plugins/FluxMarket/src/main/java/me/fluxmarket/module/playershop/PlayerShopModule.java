package me.fluxmarket.module.playershop;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.command.CommandExecutor;

import java.util.List;

public class PlayerShopModule {

    private final FluxMarket plugin;
    private final PlayerShopDao dao;
    private final PlayerShopManager manager;

    public PlayerShopModule(FluxMarket plugin) {
        this.plugin = plugin;
        this.dao = new PlayerShopDao(plugin.getDatabaseManager(), plugin.getLogger());
        this.manager = new PlayerShopManager(dao);
    }

    public void enable() {
        dao.createTable();
        manager.load();
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerShopListener(plugin, manager), plugin);
        registerCommand();
        plugin.getLogger().info("PLAYER_SHOPS module enabled. Loaded " + manager.size() + " shops.");
    }

    public void disable() {}

    private void registerCommand() {
        var cmd = plugin.getCommand("pshop");
        if (cmd == null) return;
        CommandExecutor exec = (sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("Player only.");
                return true;
            }
            if (!player.hasPermission("fluxmarket.playershop.use")) {
                player.sendMessage(FormatUtils.color(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("no-permission")));
                return true;
            }
            String prefix = plugin.getConfigManager().getPrefix();
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                var shops = manager.getByOwner(player.getUniqueId());
                if (shops.isEmpty()) {
                    player.sendMessage(FormatUtils.color(prefix + "&7You have no active shops."));
                } else {
                    player.sendMessage(FormatUtils.color(prefix + "&6Your shops (&f" + shops.size() + "&6):"));
                    for (PlayerShop s : shops) {
                        player.sendMessage(FormatUtils.color("  &7&l» &f"
                                + s.getQuantity() + "x " + FormatUtils.formatMaterialName(s.getMaterial())
                                + " &7@ &a$" + FormatUtils.formatMoney(s.getPrice())
                                + " &8(" + s.getWorldName() + " " + s.getSignX() + "," + s.getSignY() + "," + s.getSignZ() + ")"));
                    }
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("help")) {
                player.sendMessage(FormatUtils.color(prefix + "&6Player Shops Help:"));
                player.sendMessage(FormatUtils.color("  &e1. &7Place a chest."));
                player.sendMessage(FormatUtils.color("  &e2. &7Put a wall sign on the chest face."));
                player.sendMessage(FormatUtils.color("  &e3. &7Sign format:"));
                player.sendMessage(FormatUtils.color("        &fLine 1: &e[Shop]"));
                player.sendMessage(FormatUtils.color("        &fLine 2: &equantity &7(e.g. 64)"));
                player.sendMessage(FormatUtils.color("        &fLine 3: &eprice &7(e.g. $5.00)"));
                player.sendMessage(FormatUtils.color("        &fLine 4: &7(auto)"));
                player.sendMessage(FormatUtils.color("  &e4. &7Stock the chest with items."));
                player.sendMessage(FormatUtils.color("  &e5. &7Other players right-click to buy!"));
                return true;
            }
            player.sendMessage(FormatUtils.color(prefix + "&cUsage: /pshop [list|help]"));
            return true;
        };
        cmd.setExecutor(exec);
        cmd.setTabCompleter((sender, command, alias, argz) ->
                argz.length == 1 ? List.of("list", "help") : List.of());
    }

    public PlayerShopDao getDao() { return dao; }
    public PlayerShopManager getManager() { return manager; }
}
