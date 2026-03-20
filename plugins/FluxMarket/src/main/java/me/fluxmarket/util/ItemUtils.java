package me.fluxmarket.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtils {

    private ItemUtils() {}

    /** Serialize ItemStack to byte array (Paper 1.21+). */
    public static byte[] serialize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return new byte[0];
        return item.serializeAsBytes();
    }

    /** Deserialize ItemStack from byte array. Returns AIR on failure. */
    public static ItemStack deserialize(byte[] data) {
        if (data == null || data.length == 0) return new ItemStack(Material.AIR);
        try {
            return ItemStack.deserializeBytes(data);
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }

    /** Get a human-readable display name for an ItemStack. */
    public static String getDisplayName(ItemStack item) {
        if (item == null) return "Unknown";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            // Strip legacy color codes for display
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(meta.displayName());
        }
        return FormatUtils.formatMaterialName(item.getType().name());
    }

    /** Get raw display name without color stripping (for lore display). */
    public static String getDisplayNameRaw(ItemStack item) {
        if (item == null) return "Unknown";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(meta.displayName());
        }
        return "&f" + FormatUtils.formatMaterialName(item.getType().name());
    }

    public static boolean isShulkerBox(Material material) {
        return Tag.SHULKER_BOXES.isTagged(material);
    }

    /**
     * Extract all contents from a shulker box item.
     * Returns empty list if not a shulker or no contents.
     */
    public static List<ItemStack> getShulkerContents(ItemStack shulker) {
        List<ItemStack> contents = new ArrayList<>();
        if (shulker == null || !isShulkerBox(shulker.getType())) return contents;
        ItemMeta meta = shulker.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return contents;
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return contents;
        for (ItemStack item : box.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                contents.add(item.clone());
            }
        }
        return contents;
    }

    /** Build a display skull with a Base64 texture for visual graphs.
     *  The texture URL should be a textures.minecraft.net skin value in Base64. */
    public static ItemStack buildSkull(String base64Texture, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        var meta = (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();
        // Apply texture via PlayerProfile
        var profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID());
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                "textures", base64Texture));
        meta.setPlayerProfile(profile);
        meta.displayName(net.kyori.adventure.text.Component.text(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(name).content()));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(l -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(l))
                    .toList());
        }
        skull.setItemMeta(meta);
        return skull;
    }

    /** Create a simple named item with lore. */
    public static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(FormatUtils.comp(name));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore)
                    .map(FormatUtils::comp)
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Create a glass pane filler item. */
    public static ItemStack filler(Material pane) {
        ItemStack item = new ItemStack(pane);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
