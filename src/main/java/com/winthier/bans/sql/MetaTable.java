package com.winthier.bans.sql;

import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Table(name = "meta", indexes = @Index(columnList = "ban_id"))
public final class MetaTable implements SQLRow {
    @Id private Integer id;
    @Column(nullable = false) private int banId;
    @Column(nullable = false) private MetaType type;
    @Column(nullable = true) private UUID sender; // null = plugin/console
    @Column(nullable = false) private Date time;
    @Column(nullable = true, length = 4096) private String content;

    public enum MetaType {
        CREATE,
        MODIFY,
        COMMENT;
    }

    public MetaTable() { }

    public MetaTable(final int banId, final MetaType type, final UUID sender, final Date time, final String content) {
        this.banId = banId;
        this.type = type;
        this.sender = sender;
        this.time = time;
        this.content = content;
    }
}
