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
}
