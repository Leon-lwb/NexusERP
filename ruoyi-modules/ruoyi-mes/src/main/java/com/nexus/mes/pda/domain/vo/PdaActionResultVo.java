package com.nexus.mes.pda.domain.vo;

import lombok.Data;

/**
 * PDA 作业动作结果 (MES 侧, 与 erp 的 PdaActionResultVo 同构)
 */
@Data
public class PdaActionResultVo {

    private Boolean ok = Boolean.TRUE;

    private String message;

    /** 幂等重放标记: 重复请求被服务端忽略时返回 */
    private Boolean replayed;

    public static PdaActionResultVo ok() {
        return new PdaActionResultVo();
    }

    public static PdaActionResultVo ok(String message) {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.setMessage(message);
        return vo;
    }

    public static PdaActionResultVo fail(String message) {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.setOk(Boolean.FALSE);
        vo.setMessage(message);
        return vo;
    }

    public static PdaActionResultVo replayed() {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.setReplayed(Boolean.TRUE);
        return vo;
    }
}
