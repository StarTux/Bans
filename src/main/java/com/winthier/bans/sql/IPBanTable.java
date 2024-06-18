package com.winthier.bans.sql;

import com.cavetale.core.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Data
@Name("ip_bans")
public final class IPBanTable implements SQLRow {
    @Id private Integer id;
    @VarChar(40) @NotNull @Unique private String ip; // Enough for IPv6 in the future
    @Nullable private UUID admin;
    @VarChar(255) @Nullable private String reason;
    @NotNull private Date time;

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
