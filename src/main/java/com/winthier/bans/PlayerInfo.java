package com.winthier.bans;

import com.winthier.playercache.PlayerCache;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

@Data @AllArgsConstructor
public final class PlayerInfo implements Serializable {
    private UUID uuid;
    private String name;

    public PlayerInfo(final UUID uuid) {
        this.uuid = uuid;
        String n = PlayerCache.nameForUuid(uuid);
        if (n != null) {
            this.name = n;
        } else {
            this.name = "N/A";
        }
    }

    public PlayerInfo(final OfflinePlayer player) {
        this(player.getUniqueId(), player.getName());
    }

    public OfflinePlayer getPlayer() {
        return Bukkit.getServer().getOfflinePlayer(uuid);
    }
}
