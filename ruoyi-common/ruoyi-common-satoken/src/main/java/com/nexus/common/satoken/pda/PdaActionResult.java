package com.nexus.common.satoken.pda;

/**
 * PDA 动作结果 VO 契约(幂等切面据此识别"业务失败"并释放幂等键, N4)
 * <p>MES {@code MesActionResultVo} / ERP {@code PdaActionResultVo} 均实现本接口;
 * ok=false 视为失败, 切面释放幂等键允许修正后重试。</p>
 */
public interface PdaActionResult {

    /** 业务是否成功; null 视为成功 */
    Boolean getOk();
}
