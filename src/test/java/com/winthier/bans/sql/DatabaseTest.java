package com.winthier.bans.sql;

import com.winthier.sql.SQLDatabase;
import org.junit.Test;

public final class DatabaseTest {
    public void test() {
        for (var it : Database.getDatabaseClasses()) {
            System.out.println(SQLDatabase.testTableCreation(it));
        }
    }
}
