package com.nexus.mes.pda.domain.vo;

import lombok.Data;

/**
 * PDA 作业动作结果 (MES 侧, 与 erp 的 PdaActionResultVo 同构)
 */
@Data
public class MesActionResultVo {

    private Boolean ok = Boolean.TRUE;

    private String message;

    /** 幂等重放标记: 重复请求被服务端忽略时返回 */
    private Boolean replayed;

    public static MesActionResultVo ok() {
        return new MesActionResultVo();
    }

    public static MesActionResultVo ok(String message) {
        MesActionResultVo vo = new MesActionResultVo();
        vo.setMessage(message);
        return vo;
    }

    public static MesActionResultVo fail(String message) {
        MesActionResultVo vo = new MesActionResultVo();
        vo.setOk(Boolean.FALSE);
        vo.setMessage(message);
        return vo;
    }

    public static MesActionResultVo replayed() {
        MesActionResultVo vo = new MesActionResultVo();
        vo.setReplayed(Boolean.TRUE);
        return vo;
    }
}
