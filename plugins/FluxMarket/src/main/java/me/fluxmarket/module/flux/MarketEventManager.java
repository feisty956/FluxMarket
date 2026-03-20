package me.fluxmarket.module.flux;

import me.fluxmarket.FluxMarket;
import me.fluxmarket.util.FormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MarketEventManager {

    private final FluxMarket plugin;
    private final FluxEngine engine;
    private final Random random = new Random();
    private BukkitTask checkTask;
    private BukkitTask revertTask;

    public MarketEventManager(FluxMarket plugin, FluxEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public void start() {
        int interval = plugin.getConfigManager().getFluxEventCheckInterval();
        long ticks = interval * 60L * 20L;
        checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAndFireEvent, ticks, ticks);
    }

    public void stop() {
        if (checkTask != null) checkTask.cancel();
        if (revertTask != null) revertTask.cancel();
    }

    private void checkAndFireEvent() {
        FileConfiguration cfg = plugin.getConfigManager().getRaw();

        // Check CRASH
        if (cfg.getBoolean("flux.events.crash.enabled", true)) {
            double chance = cfg.getDouble("flux.events.crash.chance", 0.05);
            if (random.nextDouble() < chance) { fireEvent(MarketEvent.CRASH); return; }
        }
        // Check BOOM
        if (cfg.getBoolean("flux.events.boom.enabled", true)) {
            double chance = cfg.getDouble("flux.events.boom.chance", 0.05);
            if (random.nextDouble() < chance) { fireEvent(MarketEvent.BOOM); return; }
        }
        // Check TAX_DAY
        if (cfg.getBoolean("flux.events.tax-day.enabled", true)) {
            double chance = cfg.getDouble("flux.events.tax-day.chance", 0.03);
            if (random.nextDouble() < chance) { fireEvent(MarketEvent.TAX_DAY); return; }
        }
    }

    public void fireEvent(MarketEvent type) {
        FileConfiguration cfg = plugin.getConfigManager().getRaw();
        String prefix = plugin.getConfigManager().getPrefix();

        switch (type) {
            case CRASH -> {
                String material = pickRandomMaterial();
                if (material == null) return;
                double drop = cfg.getDouble("flux.events.crash.max-drop", 0.6);
                double modifier = 1.0 - (random.nextDouble() * drop);
                engine.applyEventModifier(material, modifier);
                int duration = cfg.getInt("flux.events.crash.duration-minutes", 30);
                String name = FormatUtils.formatMaterialName(material);
                broadcastEvent(prefix + "&c&lMARKT-CRASH! &r&c" + name + " verliert "
                        + String.format("%.0f%%", (1 - modifier) * 100) + " seines Wertes für " + duration + " Minuten!");
                schedulRevert(() -> engine.removeEventModifier(material), duration);
            }
            case BOOM -> {
                String material = pickRandomMaterial();
                if (material == null) return;
                double rise = cfg.getDouble("flux.events.boom.max-rise", 0.8);
                double modifier = 1.0 + (random.nextDouble() * rise);
                engine.applyEventModifier(material, modifier);
                int duration = cfg.getInt("flux.events.boom.duration-minutes", 30);
                String name = FormatUtils.formatMaterialName(material);
                broadcastEvent(prefix + "&a&lMARKT-BOOM! &r&a" + name + " steigt um "
                        + String.format("%.0f%%", (modifier - 1) * 100) + " für " + duration + " Minuten!");
                schedulRevert(() -> engine.removeEventModifier(material), duration);
            }
            case TAX_DAY -> {
                double reduction = cfg.getDouble("flux.events.tax-day.sell-reduction", 0.15);
                engine.setGlobalSellModifier(1.0 - reduction);
                int duration = cfg.getInt("flux.events.tax-day.duration-minutes", 30);
                broadcastEvent(prefix + "&e&lSTEUERTAG! &r&eAlle Verkaufspreise sinken um "
                        + String.format("%.0f%%", reduction * 100) + " für " + duration + " Minuten!");
                schedulRevert(engine::resetGlobalSellModifier, duration);
            }
        }
    }

    private void broadcastEvent(String message) {
        Bukkit.broadcastMessage(FormatUtils.color(message));
    }

    private void schedulRevert(Runnable action, int minutes) {
        if (revertTask != null) {
            revertTask.cancel();
            revertTask = null;
        }
        revertTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            action.run();
            revertTask = null;
        }, minutes * 60L * 20L);
    }

    private String pickRandomMaterial() {
        var raw = plugin.getConfigManager().getRaw();
        if (!raw.isConfigurationSection("shop.categories")) return null;
        List<String> materials = new ArrayList<>();
        for (String cat : raw.getConfigurationSection("shop.categories").getKeys(false)) {
            var items = raw.getConfigurationSection("shop.categories." + cat + ".items");
            if (items != null) materials.addAll(items.getKeys(false));
        }
        if (materials.isEmpty()) return null;
        return materials.get(random.nextInt(materials.size()));
    }
}
