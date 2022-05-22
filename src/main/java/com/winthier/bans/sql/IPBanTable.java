package com.winthier.bans.sql;

import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Data @Table(name = "ip_bans")
public final class IPBanTable implements SQLRow {
    @Id private Integer id;
    @Column(nullable = false, length = 40, unique = true) // Enough for IPv6 in the future
    private String ip;
    @Column(nullable = true)
    private UUID admin;
    @Column(nullable = true, length = 255)
    private String reason;
    @Column(nullable = false)
    private Date time;

    public IPBanTable() { }

    public IPBanTable(final String ip, final UUID admin, final String reason, final Date time) {
        this.ip = ip;
        this.admin = admin;
        this.reason = reason;
        this.time = time;
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
}
