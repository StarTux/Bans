package com.winthier.bans.sql;

import com.winthier.bans.BanType;
import com.winthier.playercache.PlayerCache;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Entity @Getter @Setter @Table(name = "bans")
public final class BanTable {
    @Id private Integer id;
    @Column(nullable = false) private UUID player;
    @Column(nullable = true) private UUID admin;
    @Column(nullable = true, length = 255) private String reason;
    @Column(nullable = false) private Date time;
    @Column(nullable = true) private Date expiry;
    @Version private Integer version;
    @Column(nullable = false, length = 7, name = "type") private String typeName;

    public BanTable() { }

    public BanTable(BanType type, UUID player, UUID admin, String reason, Date time, Date expiry) {
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
            OfflinePlayer op = (OfflinePlayer)sender;
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
