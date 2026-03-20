package me.fluxmarket.module.playershop;

import org.bukkit.block.Chest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for active player shops.
 * Keyed by sign location string: "world:x:y:z"
 */
public class PlayerShopManager {

    private final PlayerShopDao dao;
    /** signKey → PlayerShop */
    private final Map<String, PlayerShop> bySign = new ConcurrentHashMap<>();
    /** shopId → PlayerShop */
    private final Map<UUID, PlayerShop> byId = new ConcurrentHashMap<>();

    public PlayerShopManager(PlayerShopDao dao) {
        this.dao = dao;
    }

    public void load() {
        bySign.clear();
        byId.clear();
        for (PlayerShop shop : dao.loadAll()) {
            bySign.put(shop.signKey(), shop);
            byId.put(shop.getId(), shop);
        }
    }

    public void addShop(PlayerShop shop) {
        bySign.put(shop.signKey(), shop);
        byId.put(shop.getId(), shop);
        dao.save(shop);
    }

    public void removeBySignKey(String signKey) {
        PlayerShop shop = bySign.remove(signKey);
        if (shop != null) {
            byId.remove(shop.getId());
            dao.delete(shop.getId());
        }
    }

    public PlayerShop getBySignKey(String signKey) {
        return bySign.get(signKey);
    }

    public PlayerShop getById(UUID id) {
        return byId.get(id);
    }

    public List<PlayerShop> getByOwner(UUID ownerUuid) {
        List<PlayerShop> result = new ArrayList<>();
        for (PlayerShop shop : bySign.values()) {
            if (shop.getOwnerUuid().equals(ownerUuid)) result.add(shop);
        }
        return result;
    }

    public Collection<PlayerShop> getAll() {
        return Collections.unmodifiableCollection(bySign.values());
    }

    public int size() { return bySign.size(); }
}
