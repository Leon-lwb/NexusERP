package com.nexus.common.trace;

/**
 * 扫码所代表的主对象类型。
 */
public enum CodeObjectType {
    /**
     * 稳定实物身份对应的正式单件对象。
     */
    SN,
    /**
     * 库存批次对象，表达数量与来源范围。
     */
    BATCH,
    /**
     * 物料主数据对象，不能代替实际单件。
     */
    MATERIAL,
    /**
     * 业务单据对象，仍受场景与来源权限限制。
     */
    DOCUMENT
}
