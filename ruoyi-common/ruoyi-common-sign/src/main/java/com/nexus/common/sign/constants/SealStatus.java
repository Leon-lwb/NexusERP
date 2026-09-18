package com.nexus.common.sign.constants;

/**
 * 印章状态。
 *
 * @author nexus
 */
public final class SealStatus {

    private SealStatus() {
    }

    /** 草稿 */
    public static final String DRAFT = "0";
    /** 启用 */
    public static final String ENABLED = "1";
    /** 停用 */
    public static final String DISABLED = "2";
    /** 销毁（终态） */
    public static final String DESTROYED = "3";

    /**
     * 是否为合法状态值（状态来自请求体，入库前必须白名单校验，
     * 否则任意串会把印章卡在不可用态且无法经正常流转退出）。
     *
     * @param status 状态值
     * @return 合法返回 true
     */
    public static boolean isValid(String status) {
        return DRAFT.equals(status) || ENABLED.equals(status)
            || DISABLED.equals(status) || DESTROYED.equals(status);
    }
}
