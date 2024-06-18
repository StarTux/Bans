package com.winthier.bans.sql;

import com.cavetale.core.playercache.PlayerCache;
import com.winthier.bans.BanType;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Data
@Name("bans")
public final class BanTable implements SQLRow {
    @Id private Integer id;
    @NotNull @Keyed private UUID player;
    @Nullable private UUID admin;
    @VarChar(255) @Nullable private String reason;
    @NotNull private Date time;
    @Nullable private Date expiry;
    @NotNull @VarChar(7) @Name("type") private String typeName;

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
