package me.fluxmarket.module.shop;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.module.shop.gui.ShopCategoryGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopModule {

    private final FluxMarket plugin;
    private final ShopRegistry registry;

    public ShopModule(FluxMarket plugin) {
        this.plugin = plugin;
        this.registry = new ShopRegistry(plugin);
    }

    public void enable() {
        registry.load();

        var cmd = plugin.getCommand("shop");
        if (cmd != null) {
            cmd.setExecutor((CommandExecutor) (sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("player-only"));
                    return true;
                }
                if (!player.hasPermission("fluxmarket.shop.use")) {
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                new ShopCategoryGui(plugin, player).open();
                return true;
            });
        }
        plugin.getLogger().info("SHOP module enabled.");
    }

    public void disable() {}

    public void reload() {
        registry.load();
    }

    public ShopRegistry getRegistry() { return registry; }
}
