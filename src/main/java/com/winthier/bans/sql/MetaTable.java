package com.winthier.bans.sql;

import com.cavetale.core.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Name("meta")
public final class MetaTable implements SQLRow {
    @Id private Integer id;
    @NotNull @Keyed private int banId;
    @NotNull private MetaType type;
    @Nullable private UUID sender; // null = plugin/console
    @NotNull private Date time;
    @Text @Nullable private String content;

    @RequiredArgsConstructor
    public enum MetaType {
        CREATE("created"),
        MODIFY("modified"),
        COMMENT("commented");
        public final String passive;
    }

    public MetaTable() { }

    public MetaTable(final int banId, final MetaType type, final UUID sender, final Date time, final String content) {
        this.banId = banId;
        this.type = type;
        this.sender = sender;
        this.time = time;
        this.content = content;
    }

    public String getSenderName() {
        return sender != null
            ? PlayerCache.nameForUuid(sender)
            : "Console";
    }
}
