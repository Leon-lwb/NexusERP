package com.nexus.common.trace;
import java.util.List;
/** 稳定游标分页，不以截断后的数据宣称清单完整。 */
public record TracePage<T>(List<T> rows,String nextCursor,boolean hasMore) {
    public TracePage { rows=List.copyOf(rows); }
}
