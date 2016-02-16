package com.winthier.bans;

import com.winthier.bans.sql.BanTable;
import java.io.Serializable;
import java.util.Date;

/**
 * Simple storage class to communicate bans across servers.
 */
public class Ban implements Serializable {
    private final int id;
    private final BanType type;
    private final PlayerInfo player;
    private final PlayerInfo admin;
    private final String reason;
    private final Date time;
    private final Date expiry;

    public Ban(int id, BanType type, PlayerInfo player, PlayerInfo admin, String reason, Date time, Date expiry) {
        this.id = id;
        this.type = type;
        this.player = player;
        this.admin = admin;
        this.reason = reason;
        this.time = time;
        this.expiry = expiry;
    }

    public Ban(BanTable ban) {
        this(ban.getId(), ban.getType(), new PlayerInfo(ban.getPlayer()), ban.getAdmin() == null ? null : new PlayerInfo(ban.getAdmin()), ban.getReason(), ban.getTime(), ban.getExpiry());
    }

    public int getId() { return id; }
    public BanType getType() { return type; }
    public PlayerInfo getPlayer() { return player; }
    public PlayerInfo getAdmin() { return admin; }
    public String getReason() { return reason; }
    public Date getTime() { return time; }
    public Date getExpiry() { return expiry; }

    public String getAdminName() {
        return admin == null ? "Console" : admin.getName();
    }

    public boolean hasExpired() {
        if (expiry == null) return false;
        return expiry.compareTo(new Date()) <= 0;
    }
}
