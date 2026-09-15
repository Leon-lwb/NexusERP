package com.nexus.common.sign.constants;

/**
 * 签名业务类型。
 *
 * @author nexus
 */
public final class SignBizType {

    private SignBizType() {
    }

    /** 流程审批签批 */
    public static final String APPROVAL = "APPROVAL";
    /** MES SOP 查阅确认 */
    public static final String SOP_VIEW = "SOP_VIEW";
    /** MES NCR 处置签名 */
    public static final String NCR_DISPOSITION = "NCR_DISPOSITION";
    /** 销售合同 */
    public static final String SO_CONTRACT = "SO_CONTRACT";
    /** 采购合同 */
    public static final String PU_CONTRACT = "PU_CONTRACT";
}
