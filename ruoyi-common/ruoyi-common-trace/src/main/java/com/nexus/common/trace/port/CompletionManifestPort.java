package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

/**
 * 读取已冻结清单，ERP应在开启自身写事务前完成分页与摘要核验。
 */
public interface CompletionManifestPort {
    /**
     * 读取指定租户和修订的不可变完工清单头。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @return 指定租户与修订的不可变完工清单头。
     */
    CompletionManifest manifest(String tenantId, Long manifestId, int revision);

    /**
     * 按同一清单修订分页读取稳定行序，工单总量不受单页上限限制。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param manifestId 不可变完工清单标识。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前稳定页及其后续游标，完整结果需继续读取后续分页。
     */
    TracePage<CompletionManifestLine> lines(
            String tenantId, Long manifestId, int revision, String cursor, int limit);
}
