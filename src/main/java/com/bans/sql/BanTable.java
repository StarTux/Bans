package com.winthier.bans.sql;

import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotEmpty;
import com.avaje.ebean.validation.NotNull;
import com.winthier.bans.BanType;
import java.sql.Timestamp;
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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Entity
@Table(name = "bans")
public class BanTable {
    @Id
    private Integer id;

    @NotNull
    @ManyToOne
    private PlayerTable player;

    @ManyToOne
    private PlayerTable admin;
    
    private String reason;

    @NotNull
    private Timestamp time;

    private Timestamp expiry;

    @Version
    private Integer version;

    @NotNull
    private BanType type;

    public BanTable() {}

    public BanTable(BanType type, PlayerTable player, PlayerTable admin, String reason, Date time, Date expiry) {
        setType(type);
        setPlayer(player);
        setAdmin(admin);
        setReason(reason);
        setTime(new Timestamp(time.getTime()));
        if (expiry != null) setExpiry(new Timestamp(expiry.getTime()));
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public PlayerTable getPlayer() { return player; }
    public void setPlayer(PlayerTable player) { this.player = player; }

    public PlayerTable getAdmin() { return admin; }
    public void setAdmin(PlayerTable admin) { this.admin = admin; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Timestamp getTime() { return time; }
    public void setTime(Timestamp time) { this.time = time; }

    public Timestamp getExpiry() { return expiry; }
    public void setExpiry(Timestamp expiry) { this.expiry = expiry; }

    public BanType getType() { return type; }
    public void setType(BanType type) { this.type = type; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

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

    public void setExpiry(Date date) { setExpiry(new Timestamp(date.getTime())); }

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
