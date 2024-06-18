package com.winthier.bans.sql;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import lombok.Data;

@Data
@Name("webhook")
public final class SQLWebhook implements SQLRow {
    @Id private Integer id;
    @VarChar(255) private String url;
}
