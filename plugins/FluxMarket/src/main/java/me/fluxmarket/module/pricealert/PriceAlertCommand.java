package me.fluxmarket.module.pricealert;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PriceAlertCommand implements CommandExecutor, TabCompleter {

    private final FluxMarket plugin;

    public PriceAlertCommand(FluxMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String prefix = plugin.getConfigManager().getPrefix();

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            PriceAlertGui.open(plugin, player);
            return true;
        }

        if (args.length == 2) {
            // /pricealert <MATERIAL> <price>
            Material material;
            try {
                material = Material.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(FormatUtils.color(prefix + "&cUnknown material: &e" + args[0]
                        + "&c. Use the exact Minecraft material name (e.g. DIAMOND)."));
                return true;
            }

            double targetPrice;
            try {
                targetPrice = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(FormatUtils.color(prefix + "&cInvalid price: &e" + args[1]));
                return true;
            }

            if (targetPrice <= 0) {
                player.sendMessage(FormatUtils.color(prefix + "&cPrice must be greater than 0."));
                return true;
            }

            plugin.getPriceAlertManager().addAlert(player, material, targetPrice);
            player.sendMessage(FormatUtils.color(prefix + "&7Price alert set: notify when &e"
                    + FormatUtils.formatMaterialName(material.name())
                    + " &7drops to &a$" + FormatUtils.formatMoney(targetPrice) + " &7or below in /ah."));
            return true;
        }

        // Usage
        player.sendMessage(FormatUtils.color(prefix + "&eUsage:"));
        player.sendMessage(FormatUtils.color(prefix + "&e/pricealert <MATERIAL> <price> &7— set alert"));
        player.sendMessage(FormatUtils.color(prefix + "&e/pricealert list &7— view & manage alerts"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toUpperCase();
            if (partial.equals("L") || "LIST".startsWith(partial)) {
                List<String> result = Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(n -> n.startsWith(partial))
                        .limit(20)
                        .collect(Collectors.toList());
                result.add(0, "list");
                return result;
            }
            return Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(n -> n.startsWith(partial))
                    .limit(20)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("10", "50", "100", "500", "1000");
        }
        return List.of();
    }
}
