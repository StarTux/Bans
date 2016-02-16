package com.winthier.bans.util;

import com.winthier.bans.sql.BanTable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Bans {
    public static List<BanTable> orderByTime(List<BanTable> list) {
        BanTable[] array = list.toArray(new BanTable[0]);
        Arrays.<BanTable>sort(array, new Comparator<BanTable>() {
                @Override public int compare(BanTable o1, BanTable o2) {
                    return o1.getTime().compareTo(o2.getTime());
                }
                @Override public boolean equals(Object obj) {
                    return false;
                }
            });
        return Arrays.asList(array);
    }
}
