package com.nexus.common.trace;

import java.util.List;

/**
 * 稳定游标分页，不以截断后的数据宣称清单完整。
 *
 * @param rows 当前分页内的事实集合。
 * @param nextCursor 同一查询范围内的下一页游标。
 * @param hasMore 当前分节是否仍有后续分页。
 */
public record TracePage<T>(List<T> rows, String nextCursor, boolean hasMore) {
    public TracePage {
        rows = List.copyOf(rows);
    }
}
