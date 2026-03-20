package me.fluxmarket.module.shop;

import org.bukkit.Material;

import java.util.List;

public record ShopCategory(
        String key,
        String displayName,
        Material icon,
        List<ShopItem> items
) {}
