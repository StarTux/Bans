package com.winthier.bans.sql;

import com.winthier.bans.BanType;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Getter @Setter
@Table(name = "bans",
       indexes = @Index(columnList = "player"))
public final class BanTable implements SQLRow {
    @Id private Integer id;
    @Column(nullable = false) private UUID player;
    @Column(nullable = true) private UUID admin;
    @Column(nullable = true, length = 255) private String reason;
    @Column(nullable = false) private Date time;
    @Column(nullable = true) private Date expiry;
    @Column(nullable = false, length = 7, name = "type") private String typeName;

    public BanTable() { }

    public BanTable(final BanType type, final UUID player, final UUID admin, final String reason, final Date time, final Date expiry) {
        setType(type);
        setPlayer(player);
        setAdmin(admin);
        setReason(reason);
        setTime(time);
        setExpiry(expiry);
    }

    public BanType getType() {
        return BanType.valueOf(typeName.toUpperCase());
    }

    public void setType(BanType type) {
        this.typeName = type.key;
    }

    public String getPlayerName() {
        String name = PlayerCache.nameForUuid(player);
        if (name == null) return "N/A";
        return name;
    }

    public String getAdminName() {
        if (admin == null) return "Console";
        String name = PlayerCache.nameForUuid(admin);
        if (name == null) return "N/A";
        return name;
    }

    public boolean isOwnedBy(CommandSender sender) {
        if (admin == null) {
            return sender.equals(Bukkit.getServer().getConsoleSender());
        } else if (sender instanceof OfflinePlayer) {
            OfflinePlayer op = (OfflinePlayer) sender;
            return admin.equals(op.getUniqueId());
        }
        return false;
    }

    public boolean expires() {
        return getType().expires() && getExpiry() != null;
    }

    public boolean hasExpired() {
        if (expiry == null) return false;
        return expiry.compareTo(new Date()) <= 0;
    }

    public void lift() {
        setType(getType().lift());
    }
}
