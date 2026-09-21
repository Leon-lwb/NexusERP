package com.nexus.mes.dhr.domain;

/**
 * DHR 批记录状态机常量。
 *
 * <p>流转图：{@code DRAFT -> SUBMITTED -> (RELEASED | REJECTED)}；{@code REJECTED -> DRAFT}；
 * {@code RELEASED -> ARCHIVED}；{@code ARCHIVED} 为终态，不可再流转。</p>
 *
 * <p><b>仅 DRAFT 可 rebuild / 编辑 / 删除</b>：进入 SUBMITTED 后分册与快照哈希一律不可变
 * （21 CFR Part 11 / GMP 批记录冻结要求）。所有状态流转一律走 CAS 条件更新
 * （{@code eq(status, from)} + 判影响行数），不读-改-写。</p>
 *
 * @author Nexus
 */
public final class DhrStatus {

    private DhrStatus() {
    }

    /** 草稿（可重建/可删除，尚未冻结） */
    public static final String DRAFT = "DRAFT";
    /** 已提交复核（已签名冻结，快照不可变） */
    public static final String SUBMITTED = "SUBMITTED";
    /** 已放行（已签名） */
    public static final String RELEASED = "RELEASED";
    /** 已驳回（已签名，可退回 DRAFT 重建） */
    public static final String REJECTED = "REJECTED";
    /** 已归档（终态） */
    public static final String ARCHIVED = "ARCHIVED";

    /**
     * 判断状态流转是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 合法返回 true
     */
    public static boolean canTransit(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case DRAFT -> SUBMITTED.equals(to);
            case SUBMITTED -> RELEASED.equals(to) || REJECTED.equals(to);
            case REJECTED -> DRAFT.equals(to);
            case RELEASED -> ARCHIVED.equals(to);
            // ARCHIVED 为终态：任何流转均非法
            default -> false;
        };
    }

    /**
     * 是否已冻结（非 DRAFT 即冻结：分册/快照不可再改写）。
     *
     * @param status 状态
     * @return 已冻结返回 true
     */
    public static boolean isFrozen(String status) {
        return status != null && !DRAFT.equals(status);
    }
}
