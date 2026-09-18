package com.nexus.common.sign.support;

import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 印章可用性判定（纯逻辑，无 IO）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>印章必须为「启用」状态，且当前时间落在印章自身的 validFrom/validTo 窗口内
 *       （入库/启停时只校验了两个日期的先后顺序，挡不住「时间流逝越过 validTo」——
 *       过期或未生效的印章必须在使用时实时判定，否则有效期形同虚设）</li>
 *   <li>个人章：仅归属人本人可用</li>
 *   <li>公司章：必须存在一条有效授权（用户级或部门级匹配、状态启用、
 *       当前时间落在有效期内、bizType 为空或与本次业务类型一致）</li>
 * </ul>
 *
 * @author nexus
 */
public final class SealAvailabilityResolver {

    /** 授权启用状态 */
    private static final String GRANT_ENABLED = "1";

    private SealAvailabilityResolver() {
    }

    /**
     * 判断印章对指定用户在某业务场景下是否可用。
     *
     * @param seal     印章
     * @param grants   该印章的授权列表（可空）
     * @param userId   当前用户ID
     * @param deptId   当前用户部门ID
     * @param bizType  本次业务类型
     * @return 可用返回 true
     */
    public static boolean isUsable(EsSeal seal, List<EsSealGrant> grants,
                                   Long userId, Long deptId, String bizType) {
        if (seal == null || !SealStatus.ENABLED.equals(seal.getStatus())) {
            return false;
        }
        // 印章自身有效期实时判定（与授权窗口同一时刻取 now，判定口径一致）
        Date now = new Date();
        if (seal.getValidFrom() != null && now.before(seal.getValidFrom())) {
            return false;
        }
        if (seal.getValidTo() != null && now.after(seal.getValidTo())) {
            return false;
        }
        if (SealType.PERSONAL.equals(seal.getSealType())) {
            return Objects.equals(seal.getOwnerUserId(), userId);
        }
        if (!SealType.COMPANY.equals(seal.getSealType())) {
            return false;
        }
        if (grants == null || grants.isEmpty()) {
            return false;
        }
        for (EsSealGrant g : grants) {
            if (!GRANT_ENABLED.equals(g.getStatus())) {
                continue;
            }
            if (g.getValidFrom() != null && now.before(g.getValidFrom())) {
                continue;
            }
            if (g.getValidTo() != null && now.after(g.getValidTo())) {
                continue;
            }
            if (g.getBizType() != null && !g.getBizType().isBlank()
                && !g.getBizType().equals(bizType)) {
                continue;
            }
            boolean userMatch = g.getGranteeUserId() != null
                && Objects.equals(g.getGranteeUserId(), userId);
            boolean deptMatch = g.getGranteeDeptId() != null
                && Objects.equals(g.getGranteeDeptId(), deptId);
            if (userMatch || deptMatch) {
                return true;
            }
        }
        return false;
    }
}
