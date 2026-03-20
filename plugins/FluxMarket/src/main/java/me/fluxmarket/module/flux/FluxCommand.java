package me.fluxmarket.module.flux;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.config.ConfigManager;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FluxCommand implements CommandExecutor, TabCompleter {

    private final FluxMarket plugin;
    private final FluxEngine engine;
    private final Map<UUID, BukkitTask> activeSparklines = new HashMap<>();

    public FluxCommand(FluxMarket plugin, FluxEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        String prefix = cfg.getPrefix();

        if (args.length == 0) {
            sender.sendMessage(FormatUtils.color(prefix + "&eUsage: /market <price|trend|reset|event> [item]"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "price" -> {
                if (args.length < 2) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cUsage: /market price <item>"));
                    return true;
                }
                String material = args[1].toUpperCase();
                double base = getBasePrice(material);
                if (base < 0) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cItem nicht gefunden: " + material));
                    return true;
                }
                double pct = engine.getPercentChange(material, base);
                String trend = FormatUtils.color(FormatUtils.trendIndicator(pct));
                sender.sendMessage(FormatUtils.color(prefix + "&e" + FormatUtils.formatMaterialName(material)
                        + " &8- &fKauf: &a" + FormatUtils.formatMoney(engine.getBuyPrice(material, base, null))
                        + " &8| &fVerkauf: &c" + FormatUtils.formatMoney(engine.getSellPrice(material, base, null))
                        + " &8| ") + trend + FormatUtils.color(" &7" + FormatUtils.formatPercent(pct)));
            }
            case "trend" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cNur fuer Spieler."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cUsage: /market trend <item>"));
                    return true;
                }
                String material = args[1].toUpperCase();
                double base = getBasePrice(material);
                if (base < 0) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cItem nicht gefunden."));
                    return true;
                }
                showSparkline(player, material, base);
            }
            case "reset" -> {
                if (!sender.hasPermission("fluxmarket.market.admin")) {
                    sender.sendMessage(prefix + cfg.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cUsage: /market reset <item|all>"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    engine.invalidateAll();
                    sender.sendMessage(FormatUtils.color(prefix + "&aAlle Preis-Caches zurueckgesetzt."));
                } else {
                    engine.invalidateCache(args[1].toUpperCase());
                    sender.sendMessage(FormatUtils.color(prefix + "&aPreis-Cache fuer &e" + args[1].toUpperCase() + " &azurueckgesetzt."));
                }
            }
            case "event" -> {
                if (!sender.hasPermission("fluxmarket.market.admin")) {
                    sender.sendMessage(prefix + cfg.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cUsage: /market event <crash|boom|taxday>"));
                    return true;
                }
                MarketEventManager evtMgr = plugin.getFluxModule().getEventManager();
                try {
                    MarketEvent evt = MarketEvent.valueOf(args[1].toUpperCase().replace("TAXDAY", "TAX_DAY"));
                    evtMgr.fireEvent(evt);
                    sender.sendMessage(FormatUtils.color(prefix + "&aEvent &e" + evt.name() + " &aausgeloest."));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(FormatUtils.color(prefix + "&cUnbekanntes Event. Optionen: crash, boom, taxday"));
                }
            }
            default -> sender.sendMessage(FormatUtils.color(prefix + "&eUsage: /market <price|trend|reset|event> [item]"));
        }
        return true;
    }

    private void showSparkline(Player player, String material, double basePrice) {
        double[] history = engine.getPriceHistory(material, 8);
        double min = Arrays.stream(history).filter(v -> v > 0).min().orElse(basePrice * 0.6);
        double max = Arrays.stream(history).filter(v -> v > 0).max().orElse(basePrice * 1.4);
        String spark = FormatUtils.sparkline(history, min, max);
        double pct = engine.getPercentChange(material, basePrice);
        String trend = FormatUtils.color(FormatUtils.trendIndicator(pct));
        String name = FormatUtils.formatMaterialName(material);

        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        org.bukkit.scoreboard.Objective obj = board.registerNewObjective(
                "flux_trend", org.bukkit.scoreboard.Criteria.DUMMY,
                FormatUtils.comp("&6" + name + " &8- 24h Trend"));
        obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        String[] lines = {
                "&7" + spark,
                trend + " " + FormatUtils.color("&7") + FormatUtils.formatPercent(pct),
                "&8Kauf: &a" + FormatUtils.formatMoney(engine.getBuyPrice(material, basePrice, null)),
                "&8Verkauf: &c" + FormatUtils.formatMoney(engine.getSellPrice(material, basePrice, null)),
                "&8Basis: &e" + FormatUtils.formatMoney(basePrice)
        };
        int score = lines.length;
        for (String line : lines) {
            obj.getScore(FormatUtils.color(line)).setScore(score--);
        }

        org.bukkit.scoreboard.Scoreboard previous = player.getScoreboard();
        player.setScoreboard(board);

        BukkitTask prev = activeSparklines.remove(player.getUniqueId());
        if (prev != null) prev.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.setScoreboard(previous);
            activeSparklines.remove(player.getUniqueId());
        }, 200L);
        activeSparklines.put(player.getUniqueId(), task);
    }

    private double getBasePrice(String material) {
        var raw = plugin.getConfigManager().getRaw();
        if (!raw.isConfigurationSection("shop.categories")) return -1;
        for (String cat : raw.getConfigurationSection("shop.categories").getKeys(false)) {
            String path = "shop.categories." + cat + ".items." + material + ".base-price";
            if (raw.contains(path)) return raw.getDouble(path);
        }
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("price", "trend", "reset", "event");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "price", "trend", "reset" -> getAllMaterials();
                case "event" -> List.of("crash", "boom", "taxday");
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> getAllMaterials() {
        var raw = plugin.getConfigManager().getRaw();
        List<String> result = new ArrayList<>();
        if (!raw.isConfigurationSection("shop.categories")) return result;
        for (String cat : raw.getConfigurationSection("shop.categories").getKeys(false)) {
            var items = raw.getConfigurationSection("shop.categories." + cat + ".items");
            if (items != null) result.addAll(items.getKeys(false));
        }
        result.add("all");
        return result;
    }
}
