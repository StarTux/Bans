package com.winthier.bans.sql;

import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Data
@NotNull
@Name("silent_bans")
public final class SQLSilentBan implements SQLRow {
    @Id private Integer id;
    @Unique private UUID uuid;
    private Date created;
}
