package com.nexus.common.sign.support;

import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 印章可用性判定测试。
 *
 * @author nexus
 */
class SealAvailabilityResolverTest {

    private static final long ME = 7L;
    private static final long MY_DEPT = 100L;
    private static final String BIZ = "APPROVAL";

    private static Date daysFromNow(int d) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, d);
        return c.getTime();
    }

    private static EsSeal personal(Long owner, String status) {
        EsSeal s = new EsSeal();
        s.setSealId(1L);
        s.setSealType(SealType.PERSONAL);
        s.setOwnerUserId(owner);
        s.setStatus(status);
        return s;
    }

    private static EsSeal company(String status) {
        EsSeal s = new EsSeal();
        s.setSealId(2L);
        s.setSealType(SealType.COMPANY);
        s.setOwnerUserId(null);
        s.setStatus(status);
        return s;
    }

    private static EsSealGrant grant(Long userId, Long deptId, String bizType, String status, Date from, Date to) {
        EsSealGrant g = new EsSealGrant();
        g.setGrantId(50L);
        g.setSealId(2L);
        g.setGranteeUserId(userId);
        g.setGranteeDeptId(deptId);
        g.setBizType(bizType);
        g.setStatus(status);
        g.setValidFrom(from);
        g.setValidTo(to);
        return g;
    }

    @Test
    @DisplayName("个人章：本人 + 启用 -> 可用")
    void personal_ownerAndEnabled_available() {
        assertTrue(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：非本人 -> 不可用")
    void personal_notOwner_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(999L, SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：停用 -> 不可用")
    void personal_disabled_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.DISABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：销毁 -> 不可用")
    void personal_destroyed_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.DESTROYED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：无授权 -> 不可用")
    void company_noGrant_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：用户级授权 + 启用 -> 可用")
    void company_userGrant_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：部门级授权 -> 可用")
    void company_deptGrant_available() {
        List<EsSealGrant> grants = List.of(
            grant(null, MY_DEPT, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权已过期 -> 不可用")
    void company_expiredGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-10), daysFromNow(-1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权未生效 -> 不可用")
    void company_futureGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(1), daysFromNow(10)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权已停用 -> 不可用")
    void company_disabledGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "0", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权限定其他场景 -> 不可用")
    void company_grantOtherBizType_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, "SO_CONTRACT", "1", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权限定本场景 -> 可用")
    void company_grantSameBizType_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, BIZ, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权不限场景(NULL) -> 可用")
    void company_grantNullBizType_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权人非我且部门不匹配 -> 不可用")
    void company_grantOtherUserAndDept_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(888L, 999L, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：无有效期限制(均为NULL) -> 可用")
    void company_noValidityWindow_available() {
        List<EsSealGrant> grants = List.of(grant(ME, null, null, "1", null, null));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：grants 为 null -> 不可用且不抛异常")
    void company_nullGrants_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), null, ME, MY_DEPT, BIZ));
    }
}
