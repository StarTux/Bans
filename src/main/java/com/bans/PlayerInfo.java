package com.winthier.bans;

import com.winthier.bans.sql.PlayerTable;
import java.io.Serializable;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PlayerInfo implements Serializable {
    private final UUID uuid;
    private final String name;

    public PlayerInfo(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public PlayerInfo(OfflinePlayer player) {
        this(player.getUniqueId(), player.getName());
    }

    public PlayerInfo(PlayerTable player) {
        this(player.getUuid(), player.getName());
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public OfflinePlayer getPlayer() {
        return Bukkit.getServer().getOfflinePlayer(uuid);
    }
}
