package com.winthier.bans.sql;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import org.bukkit.OfflinePlayer;

@Entity
@Table(name = "players")
public class PlayerTable {
    @Id
    private Integer id;

    @Column(nullable = false)
    private UUID uuid;

    @Column(nullable = false, length = 16)
    private String name;

    @Column(nullable = false)
    private Timestamp addTime;

    @Version
    private Integer version;

    public PlayerTable() {}

    public PlayerTable(UUID uuid, Date addTime) {
        setUuid(uuid);
        setAddTime(new Timestamp(addTime.getTime()));
    }

    public PlayerTable(UUID uuid, String name, Date addTime) {
        this(uuid, addTime);
        setName(name);
    }

    public PlayerTable(OfflinePlayer player, Date addTime) {
        this(player.getUniqueId(), player.getName(), addTime);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<BanTable> getBans() {
        return Database.getInstance().getDb().find(BanTable.class).eq("player", this).findList();
    }

    public Timestamp getAddTime() { return addTime; }
    public void setAddTime(Timestamp addTime) { this.addTime = addTime; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
