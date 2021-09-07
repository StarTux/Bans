package com.winthier.bans;

import com.winthier.bans.sql.BanTable;
import java.io.Serializable;
import java.util.Date;
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
}