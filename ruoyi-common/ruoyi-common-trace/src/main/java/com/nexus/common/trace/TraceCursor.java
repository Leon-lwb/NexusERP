package com.nexus.common.trace;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 分页游标绑定租户、查询主体和分节；游标只表示位置，权限仍由调用入口核验。
 */
public final class TraceCursor {
    private TraceCursor() {}

    /**
     * 将租户、主体、分节和位置绑定为分页游标，游标不携带权限。
     *
     * @param tenant 当前认证租户。
     * @param id 所属快照记录的稳定主键。
     * @param section 当前追溯事实分节。
     * @param after 上次处理完成的稳定位置。
     * @return 绑定查询范围和当前位置的编码游标。
     */
    public static String encode(String tenant, Long id, String section, long after) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        (tenant + ":" + id + ":" + section + ":" + after)
                                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 核对游标与原查询范围一致，避免跨租户、跨主体或跨修订使用。
     *
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param tenant 当前认证租户。
     * @param id 所属快照记录的稳定主键。
     * @param section 当前追溯事实分节。
     * @return 本次业务关联或新建的稳定记录标识。
     */
    public static long decode(String cursor, String tenant, Long id, String section) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String[] fields =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                            .split(":", -1);
            if (fields.length != 4
                    || !fields[0].equals(tenant)
                    || !fields[1].equals(id.toString())
                    || !fields[2].equals(section)) {
                throw new IllegalArgumentException();
            }
            long after = Long.parseLong(fields[3]);
            if (after <= 0) {
                throw new IllegalArgumentException();
            }
            return after;
        } catch (Exception e) {
            throw new IllegalArgumentException("追溯游标不属于当前身份或范围");
        }
    }
}
