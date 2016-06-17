package com.winthier.bans;

import com.winthier.bans.sql.PlayerTable;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
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

    public Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", uuid.toString());
        result.put("name", name);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static PlayerInfo deserialize(Map<String, Object> map) {
        UUID uuid = Ban.fetchUuid(map, "uuid");
        String name = Ban.fetchString(map, "name");
        return new PlayerInfo(uuid, name);
    }
}
