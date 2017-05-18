package com.winthier.bans.sql;

import com.winthier.bans.BanType;
import java.util.Date;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Entity @Getter @Setter @Table(name = "bans")
public class BanTable {
    @Id
    private Integer id;

    @Column(nullable = false)
    @ManyToOne
    private PlayerTable player;

    @ManyToOne
    private PlayerTable admin;

    private String reason;

    @Column(nullable = false)
    private Date time;

    private Date expiry;

    @Version
    private Integer version;

    @Column(nullable = false, length = 7, name = "type")
    private String typeName;

    public BanTable() {}

    public BanTable(BanType type, PlayerTable player, PlayerTable admin, String reason, Date time, Date expiry) {
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

    public String getAdminName() {
        return admin == null ? "Console" : admin.getName();
    }

    public boolean isOwnedBy(CommandSender sender) {
        if (admin == null) {
            return sender.equals(Bukkit.getServer().getConsoleSender());
        } else if (sender instanceof OfflinePlayer) {
            OfflinePlayer player = (OfflinePlayer)sender;
            return admin.getUuid().equals(player.getUniqueId());
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
