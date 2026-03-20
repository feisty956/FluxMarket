package me.fluxmarket.module.orders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class OrderSignInput implements Listener {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static void open(Player player, String title, String hint, String initialValue, Consumer<String> callback) {
        Location signLocation = findSignLocation(player);
        if (signLocation == null) {
            callback.accept(null);
            return;
        }

        Block block = signLocation.getBlock();
        BlockState originalState = block.getState();
        block.setType(Material.OAK_SIGN, false);

        Sign sign = (Sign) block.getState();
        sign.line(0, Component.text(title));
        sign.line(1, Component.text(hint));
        sign.line(2, Component.text(initialValue == null ? "" : initialValue));
        sign.line(3, Component.text("Confirm"));
        sign.update(true, false);

        SESSIONS.put(player.getUniqueId(), new Session(block.getLocation(), originalState, callback));
        player.openSign(sign);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Session session = SESSIONS.remove(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (!event.getBlock().getLocation().equals(session.location())) {
            SESSIONS.put(event.getPlayer().getUniqueId(), session);
            return;
        }

        String value = firstContentLine(event.getLines());
        restore(session);
        session.callback().accept(value == null || value.isBlank() ? null : value.trim());
    }

    private static String firstContentLine(String[] lines) {
        for (int i = 2; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isBlank()) return lines[i];
        }
        for (String line : lines) {
            if (line != null && !line.isBlank()) return line;
        }
        return null;
    }

    private static void restore(Session session) {
        session.originalState().update(true, false);
    }

    private static Location findSignLocation(Player player) {
        Location origin = player.getLocation().getBlock().getLocation();
        List<Vector> offsets = List.of(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(-1, 0, 0),
                new Vector(0, 0, 1),
                new Vector(0, 0, -1),
                new Vector(1, 0, 1),
                new Vector(-1, 0, -1),
                new Vector(1, 0, -1),
                new Vector(-1, 0, 1),
                new Vector(0, 1, 0),
                new Vector(1, 1, 0),
                new Vector(-1, 1, 0),
                new Vector(0, 1, 1),
                new Vector(0, 1, -1)
        );

        for (Vector offset : offsets) {
            Location base = origin.clone().add(offset);
            Block below = base.getBlock();
            Block signBlock = base.clone().add(0, 1, 0).getBlock();
            if (!below.getType().isAir() && signBlock.getType().isAir()) {
                return signBlock.getLocation();
            }
        }
        return null;
    }

    private record Session(Location location, BlockState originalState, Consumer<String> callback) {}
}
