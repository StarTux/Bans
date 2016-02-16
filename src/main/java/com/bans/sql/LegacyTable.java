package com.winthier.bans.sql;

import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotEmpty;
import com.avaje.ebean.validation.NotNull;
import com.winthier.bans.BanType;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import org.bukkit.OfflinePlayer;

@Entity
@Table(name = "legacy")
public class LegacyTable {
    @Id
    private Integer id;

    @NotEmpty
    @Length(max=16)
    private String player;

    @NotEmpty
    @Length(max=16)
    private String admin;

    private String reason;

    @NotNull
    private Timestamp time;

    private Timestamp expiry;

    private String type;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public String getAdmin() { return admin; }
    public void setAdmin(String admin) { this.admin = admin; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Timestamp getTime() { return time; }
    public void setTime(Timestamp time) { this.time = time; }

    public Timestamp getExpiry() { return expiry; }
    public void setExpiry(Timestamp expiry) { this.expiry = expiry; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
