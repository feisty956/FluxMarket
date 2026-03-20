package me.fluxmarket.module.sell.wand;

import java.util.UUID;

public class SellWand {

    public enum WandType { USE, TIME }

    private final UUID uuid;
    private final UUID ownerUuid;
    private final String ownerName;
    private final WandType type;
    private int usesRemaining;
    private final long expiresAt;
    private final long createdAt;

    public SellWand(UUID uuid, UUID ownerUuid, String ownerName, WandType type,
                    int usesRemaining, long expiresAt) {
        this.uuid = uuid;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.type = type;
        this.usesRemaining = usesRemaining;
        this.expiresAt = expiresAt;
        this.createdAt = System.currentTimeMillis();
    }

    // DB reconstruction constructor
    public SellWand(UUID uuid, UUID ownerUuid, String ownerName, WandType type,
                    int usesRemaining, long expiresAt, long createdAt) {
        this.uuid = uuid;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.type = type;
        this.usesRemaining = usesRemaining;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isValid() {
        return type == WandType.USE ? usesRemaining > 0 : System.currentTimeMillis() < expiresAt;
    }

    public void decrementUse() {
        if (type == WandType.USE) usesRemaining--;
    }

    // Getters
    public UUID getUuid()          { return uuid; }
    public UUID getOwnerUuid()     { return ownerUuid; }
    public String getOwnerName()   { return ownerName; }
    public WandType getType()      { return type; }
    public int getUsesRemaining()  { return usesRemaining; }
    public long getExpiresAt()     { return expiresAt; }
    public long getCreatedAt()     { return createdAt; }
}
