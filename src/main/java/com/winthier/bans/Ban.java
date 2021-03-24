package com.winthier.bans;

import com.winthier.bans.sql.BanTable;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

/**
 * Simple storage class to communicate bans across servers.
 */
@Getter
public final class Ban implements Serializable {
    private final int id;
    private final BanType type;
    private final PlayerInfo player;
    private final PlayerInfo admin;
    private final String reason;
    private final Date time;
    private final Date expiry;

    public Ban(final int id, final BanType type, final PlayerInfo player, final PlayerInfo admin, final String reason, final Date time, final Date expiry) {
        this.id = id;
        this.type = type;
        this.player = player;
        this.admin = admin;
        this.reason = reason;
        this.time = time;
        this.expiry = expiry;
    }

    public Ban(final BanTable ban) {
        this(ban.getId(), ban.getType(),
             new PlayerInfo(ban.getPlayer()), ban.getAdmin() == null ? null : new PlayerInfo(ban.getAdmin()),
             ban.getReason(), ban.getTime(), ban.getExpiry());
    }

    public String getAdminName() {
        return admin == null ? "Console" : admin.getName();
    }

    public boolean hasExpired() {
        if (expiry == null) return false;
        return expiry.compareTo(new Date()) <= 0;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        store(map, "id", id);
        store(map, "type", type.name());
        store(map, "player", player);
        store(map, "admin", admin);
        store(map, "reason", reason);
        store(map, "time", time);
        store(map, "expiry", expiry);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Ban deserialize(Map<String, Object> map) {
        int id = fetchInt(map, "id");
        BanType type = BanType.valueOf(fetchString(map, "type"));
        PlayerInfo player = fetchPlayerInfo(map, "player");
        PlayerInfo admin = fetchPlayerInfo(map, "admin");
        String reason = fetchString(map, "reason");
        Date time = fetchDate(map, "time");
        Date expiry = fetchDate(map, "expiry");
        return new Ban(id, type, player, admin, reason, time, expiry);
    }

    @SuppressWarnings("unchecked")
    private static void store(Map<String, Object> map, String key, Object value) {
        if (value == null) return;
        if (value instanceof UUID) {
            map.put(key, ((UUID) value).toString());
        } else if (value instanceof PlayerInfo) {
            map.put(key, ((PlayerInfo) value).serialize());
        } else if (value instanceof Date) {
            map.put(key, ((Date) value).getTime());
        } else {
            map.put(key, value);
        }
    }

    static String fetchString(Map<String, Object> map, String key) {
        Object result = map.get(key);
        return result == null ? null : result.toString();
    }

    static int fetchInt(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        return Integer.parseInt(o.toString());
    }

    static UUID fetchUuid(Map<String, Object> map, String key) {
        String result = fetchString(map, key);
        return result == null ? null : UUID.fromString(result);
    }

    static Date fetchDate(Map<String, Object> map, String key) {
        Object result = map.get(key);
        if (result instanceof Number) {
            return new Date(((Number) result).longValue());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static PlayerInfo fetchPlayerInfo(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o == null) return null;
        return PlayerInfo.deserialize((Map<String, Object>) o);
    }
}
